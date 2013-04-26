package org.eigengo.akkaextras.javamail

import akka.actor.Actor
import javax.mail.internet.MimeMessage

trait SimpleUnconfiguredActorEmail extends JavamailEmailTransport with SimpleInternetAddressBuilder with SimpleMimeMessageBodyBuilder with SimpleMimeMessageBuilder {
  this: EmailConfiguration =>
}

trait SimpleConfgiruedActorEmail extends SimpleUnconfiguredActorEmail with ConfigEmailConfiguration {
  this: Actor =>

  def config = context.system.settings.config
}

/**
 * @author janmachacek
 */
class EmailActor extends Actor with Emailer {
  this: EmailTransport with EmailConfiguration with MimeMessageBuilder with MimeMessageBodyBuilder with InternetAddressBuilder =>

  def receive = {
    case m: MimeMessage => transportEmailMessage(m).run
    case m: MessageIn => email(m).run
  }

}
