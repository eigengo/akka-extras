package org.eigengo.akkaextras.javamail

import scalaz.Id._
import org.specs2.mutable.Specification
import javax.mail.internet.{InternetAddress, MimeMultipart}

trait AttachmentMimeMessageBodyBuilder extends MimeMessageBodyBuilder {
  type MimeMessageBodyIn = MailBody

  def buildMimeMessageBody: MimeMessageBodyIn => MimeMultipart = { body =>
    new MimeMultipart("foo")
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

class JavamailEmailMessageDeliverySpec extends Specification
  with JavamailEmailMessageDelivery with EmailConfiguration
  with UserInternetAddressBuilder with AttachmentMimeMessageBodyBuilder with SimpleMimeMessageBuilder {
  def getMailSession = ???

  "foo" in {
    val from = User("Jan", "janm@cakesolutions.net")
    val x =
    for {
      message <- buildMimeMessage(from, "Test", MailBody("Test", "<html>Test</html"), List(from), Nil, Nil)
      result <- transportEmailMessage(message)
    } yield (result)

    x.run
  }
}
