package org.eigengo.akkaextras.javamail

import scalaz.Id._
import org.specs2.mutable.Specification
import javax.mail.internet.{InternetAddress, MimeMultipart}
import javax.mail.Session
import java.util.Properties

trait AttachmentMimeMessageBodyBuilder extends MimeMessageBodyBuilder {
  import scalaz.syntax.monad._
  type MimeMessageBodyIn = MailBody

  def buildMimeMessageBody: MimeMessageBodyIn => EitherFailures[MimeMultipart] = { body =>
    new MimeMultipart("foo").point[EitherFailures]
  }
}

case class MailBody(plain: String, html: String)

// --

case class User(firstName: String, email: String)

trait UserInternetAddressBuilder extends InternetAddressBuilder {
  type AddressIn = User

  /**
   * Returns function that takes the input and produces errors on the left or ``InternetAddress`` on the right
   * @return the function that, ultimately, produces the ``InternetAddress``es
   */
  def buildInternetAddress = { user => scalaz.EitherT.fromTryCatch[Id, InternetAddress](InternetAddress.parse(user.email)(0)) }
}

trait TestingEmailConfiguration extends EmailConfiguration {
  import scalaz.syntax.monad._
  def getMailSession = Session.getInstance(new Properties()).point[EitherFailures]
}

class JavamailEmailMessageDeliverySpec extends Specification {

  class Simple extends JavamailEmailMessageDelivery with TestingEmailConfiguration with SimpleInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder

  class Custom extends JavamailEmailMessageDelivery with TestingEmailConfiguration with UserInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder

  "Constructs and sends Simple* email" in {
    val from = "Jan Machacek <janm@cakesolutions.net>"
    val simple = new Simple
    val x =
    for {
      message <- simple.buildMimeMessage(from, "Test", "Test Body", List(from), Nil, Nil)
      result  <- simple.transportEmailMessage(message)
    } yield result

    x.run.toEither match {
      case Left(t) => println(t)
      case Right(()) =>
    }

    success
  }
}
