package org.eigengo.akkaextras.javamail

import javax.mail.internet.{MimeBodyPart, MimeMultipart, InternetAddress, MimeMessage}
import scalaz.EitherT._
import scalaz.EitherT
import scalaz.Id._
import javax.mail._
import com.typesafe.config.Config
import java.util.Properties

/**
 * Implementations transport the
 *
 */
trait EmailMessageTransport {

  def transportEmailMessage(message: MimeMessage): EitherT[Id, Throwable, Unit]

}


/**
 * Configures the email session; the session must be configured and ready to produce the mime messages
 */
trait EmailConfiguration {

  /**
   * Constructs fully initialised [[javax.mail.Session]].
   *
   * @return the properly configured session
   */
  def getMailSession: EitherT[Id, Throwable, Session]

}

/**
 * The Typesafe [[com.typesafe.config.Config]] implementation of ``EmailConfiguration``
 */
trait ConfigEmailConfiguration extends EmailConfiguration {
  def config: Config

  def getMailSession = {
    val props = getMailProperties
    val username = config.getString("akka-extras.javamail.username")
    val password = config.getString("akka-extras.javamail.password")
    val authenticator = new Authenticator {
          override def getPasswordAuthentication =
            new PasswordAuthentication(username, password)
        }

    fromTryCatch[Id, Session](Session.getInstance(props, authenticator))
  }

  private def getMailProperties = {
    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", "smtp.gmail.com")
    props.put("mail.smtp.port", "587")

    props
  }

}

/**
 * Constructs ``MimeMessage``s from some input
 */
trait MimeMessageBuilder {
  /**
   * The input type
   */
  type MessageIn

  /**
   * Returns function that takes the input and produces errors on the left or ``MimeMessage`` on the right
   * @return the function that, ultimately, produces the ``MimeMessage``
   */
  def buildMimeMessage: MessageIn => EitherT[Id, Throwable, MimeMessage]

}

/**
 * Constructs ``InternetAddress``es from some input
 */
trait InternetAddressBuilder {
  /**
   * The input type
   */
  type AddressIn

  /**
   * Returns function that takes the input and produces errors on the left or ``InternetAddress`` on the right
   * @return the function that, ultimately, produces the ``InternetAddress``es
   */
  def buildInternetAddress: AddressIn => EitherT[Id, Throwable, InternetAddress]

  def buildInternetAddresses: List[AddressIn] => EitherT[Id, Throwable, Array[InternetAddress]] = { addresses =>
    val z = Array.empty[InternetAddress]
    right[Id, Throwable, Array[InternetAddress]](z)
  }

}

trait MimeMessageBodyBuilder {
  type MimeMessageBodyIn

  def buildMimeMessageBody: MimeMessageBodyIn => EitherT[Id, Throwable, MimeMultipart]
}

trait SimpleMimeMessageBodyBuilder extends MimeMessageBodyBuilder {
  type MimeMessageBodyIn = String

  def buildMimeMessageBody: MimeMessageBodyIn => EitherT[Id, Throwable, MimeMultipart] = { body =>
    val multipart = new MimeMultipart("alternative")
    val plainTextPart = new MimeBodyPart()
    plainTextPart.setContent(body, "text/plain")
    multipart.addBodyPart(plainTextPart)

    val textHtmlPart = new MimeBodyPart()
    textHtmlPart.setContent(body, "text/html")
    multipart.addBodyPart(textHtmlPart)

    right[Id, Throwable, MimeMultipart](multipart)
  }
}


trait SimpleInternetAddressBuilder extends InternetAddressBuilder {
  type AddressIn = String

  def buildInternetAddress: AddressIn => EitherT[Id, Throwable, InternetAddress] = { address: AddressIn =>
    fromTryCatch[Id, InternetAddress](InternetAddress.parse(address, false)(0))
  }
}

trait SimpleMimeMessageBuilder extends MimeMessageBuilder {
  this: EmailConfiguration with InternetAddressBuilder with MimeMessageBodyBuilder =>

  /**
   * The input is _from_, _subject_, _body_, _to_, _cc_, _bcc_
   */
  type MessageIn = (AddressIn, String, MimeMessageBodyIn, List[AddressIn], List[AddressIn], List[AddressIn])

  def mimeMessage(session: Session, from: InternetAddress, subject: String, body: MimeMultipart,
                  to: Array[InternetAddress], cc: Array[InternetAddress], bcc: Array[InternetAddress]): MimeMessage = {
    val message =  new MimeMessage(session)
    message.setSubject(subject)
    message.setContent(body)
    message.setFrom(from)
    message.addRecipients(Message.RecipientType.TO, to.map(_.asInstanceOf[Address]))
    message.addRecipients(Message.RecipientType.CC, cc.map(_.asInstanceOf[Address]))
    message.addRecipients(Message.RecipientType.BCC, bcc.map(_.asInstanceOf[Address]))
    message
  }

  def buildMimeMessage: MessageIn => EitherT[Id, Throwable, MimeMessage] = { in: MessageIn =>

    val (from, subject, body, to, cc, bcc) = in

    for {
      session     <- getMailSession
      body        <- buildMimeMessageBody(body)
      from        <- buildInternetAddress(from)
      to          <- buildInternetAddresses(to)
      cc          <- buildInternetAddresses(cc)
      bcc         <- buildInternetAddresses(bcc)
    } yield mimeMessage(session, from, subject, body, to, cc, bcc)

  }
}

/**
 * Simple email message delivery system for temporary usage.
 */
trait JavamailEmailMessageDelivery extends EmailMessageTransport {
  this: EmailConfiguration =>


  /**
   * Transports the ``message`` with the appropriate values
   *
   * @param message the message to send
   * @return ()
   */
  def transportEmailMessage(message: MimeMessage): EitherT[Id, Throwable, Unit] = {
    scalaz.EitherT.fromTryCatch[Id, Unit](Transport.send(message))
  }
}
