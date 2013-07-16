package org.eigengo.akkaextras.javamail

import javax.mail.internet.{MimeBodyPart, MimeMultipart, InternetAddress, MimeMessage}
import scalaz.EitherT._
import scalaz.Id._
import javax.mail._
import com.typesafe.config.Config
import java.util.Properties

/**
 * Implementations transport the ``message`` to its recipients
 */
trait EmailTransport {

  /**
   * Sends the ``message`` to its recipients
   * @param message the message to be sent
   * @return ()
   */
  def transportEmailMessage(message: MimeMessage): EitherFailures[Unit]

}


/**
 * Configures the email session; the session must be configured and ready to produce the mime messages
 */
trait EmailConfiguration {

  /**
   * Constructs fully initialised [[javax.mail.Session]].
   * @return the properly configured session
   */
  def getMailSession: EitherFailures[Session]

}

/**
 * The Typesafe [[com.typesafe.config.Config]] implementation of ``EmailConfiguration``
 */
trait ConfigEmailConfiguration extends EmailConfiguration {
  def config: Config

  def getMailSession = {
    val props = buildMailProperties()
    if (config.hasPath("akka-extras.javamail.authenticate")) {
      val username = config.getString("akka-extras.javamail.authenticate.username")
      val password = config.getString("akka-extras.javamail.authenticate.password")
      val authenticator = new Authenticator {
            override def getPasswordAuthentication =
              new PasswordAuthentication(username, password)
          }
      fromTryCatch[Id, Session](Session.getInstance(props, authenticator))
    } else {
      fromTryCatch[Id, Session](Session.getInstance(props))
    }


  }


  private def buildMailProperties(): java.util.Properties = {
    val props = new Properties()
    if (config.hasPath("akka-extras.javamail.properties")) {
      val properties = config.getConfig("akka-extras.javamail.properties")
      import scala.collection.JavaConversions._
      properties.entrySet().foreach { entry =>
        props.put(entry.getKey, entry.getValue.render().replaceAll("\"",""))
      }
    }
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
  type MessageIn <: AnyRef

  /**
   * Returns function that takes the input and produces errors on the left or ``MimeMessage`` on the right
   * @return the function that, ultimately, produces the ``MimeMessage``
   */
  def buildMimeMessage: MessageIn => EitherFailures[MimeMessage]

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
  def buildInternetAddress: AddressIn => EitherFailures[InternetAddress]

  def buildInternetAddresses: List[AddressIn] => EitherFailures[Array[InternetAddress]] = { addresses =>
    import scalaz.syntax.monad._

    val z = List.empty[InternetAddress].point[EitherFailures]
    addresses.map(buildInternetAddress).foldLeft(z) { (b, a) =>
      for {
        address   <- a
        addresses <- b
      } yield address :: addresses
    }.map(_.toArray)
  }

}

/**
 * Constructs the ``MimeMultipart`` message from some input
 */
trait MimeMessageBodyBuilder {
  /**
   * The input type
   */
  type MimeMessageBodyIn

  /**
   * Returns function that takes hte input and produces errors on the left or ``MimeMultipart`` on the right
   * @return the function that, ultimately, produces the ``MimeMultipart`` from the input
   */
  def buildMimeMessageBody: MimeMessageBodyIn => EitherFailures[MimeMultipart]
}

trait SimpleMimeMessageBodyBuilder extends MimeMessageBodyBuilder {
  type MimeMessageBodyIn = String

  def buildMimeMessageBody: MimeMessageBodyIn => EitherFailures[MimeMultipart] = { body =>
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

  def buildInternetAddress: AddressIn => EitherFailures[InternetAddress] = { address: AddressIn =>
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

  def buildMimeMessage: MessageIn => EitherFailures[MimeMessage] = { in: MessageIn =>

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
trait JavamailEmailTransport extends EmailTransport {
  this: EmailConfiguration =>


  /**
   * Transports the ``message`` with the appropriate values
   *
   * @param message the message to send
   * @return ()
   */
  def transportEmailMessage(message: MimeMessage): EitherFailures[Unit] = {
    fromTryCatch[Id, Unit](Transport.send(message))
  }
}

trait Emailer {
  this: EmailTransport with EmailConfiguration with MimeMessageBuilder with MimeMessageBodyBuilder with InternetAddressBuilder =>

  def email: MessageIn => EitherFailures[Unit] = { message => buildMimeMessage(message).flatMap(transportEmailMessage) }

}

/**
 * Constructs the "simple" email structure. Simple in this case means that the addresses and body are ``String``s. You
 * still need to provide the appropriate ``EmailConfiguration``.
 */
trait SimpleUnconfiguredEmail extends JavamailEmailTransport with SimpleInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder {
  this: EmailConfiguration =>
}
