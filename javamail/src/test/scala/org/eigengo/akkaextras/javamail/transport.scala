package org.eigengo.akkaextras.javamail

import scalaz.Id._
import org.specs2.mutable.Specification
import javax.mail.internet.{InternetAddress, MimeMultipart}

trait AttachmentMimeMessageBodyBuilder extends MimeMessageBodyBuilder {
  import scalaz.syntax.monad._
  type MimeMessageBodyIn = MailBody

  def buildMimeMessageBody: MimeMessageBodyIn => EitherFailures[MimeMultipart] = { body =>
    new MimeMultipart("foo").point[EitherFailures]
  }
}

case class MailBody(plain: String, html: String)

case class User(firstName: String, email: String)

trait UserInternetAddressBuilder extends InternetAddressBuilder {
  type AddressIn = User

  /**
   * Returns function that takes the input and produces errors on the left or ``InternetAddress`` on the right
   * @return the function that, ultimately, produces the ``InternetAddress``es
   */
  def buildInternetAddress = { user => scalaz.EitherT.fromTryCatch[Id, InternetAddress](InternetAddress.parse(user.email)(0)) }
}

// --

class JavamailEmailMessageDeliverySpec extends Specification with EmailFragments {

  class Simple extends Emailer with JavamailEmailTransport with TestingEmailConfiguration with SimpleInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder

  class Custom extends Emailer with JavamailEmailTransport with TestingEmailConfiguration with UserInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder

  "SMTP email transport" should {
    sequential

    "construct and send simple emails" in {
      val subject = "Subject"
      val body = "Body"
      val email = receiveEmails {
        val from = "Jan Machacek <janm@cakesolutions.net>"

        val simple = new Simple
        val x = simple.email(from, subject, body, List(from), Nil, Nil)
        x.run.isRight must beTrue
      }.head

      email.getHeaderValue("Subject") mustEqual subject
    }
  }
}
