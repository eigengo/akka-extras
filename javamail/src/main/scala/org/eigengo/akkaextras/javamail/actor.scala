package org.eigengo.akkaextras.javamail

import akka.actor.Actor
import javax.mail.internet.MimeMessage

/**
 * Implementation of the ``SimpleUnconfiguredEmail`` that reads its configuration from the ``ActorSystem``'s configuration.
 * You will typically mix this with your ``EmailActor`` instances as
 * {{{
 * system.actorOf(new EmailActor with SimpleConfiguredActorEmail)
 * }}}
 */
trait SimpleConfiguredActorEmail extends SimpleUnconfiguredEmail with ConfigEmailConfiguration {
  this: Actor =>

  def config = context.system.settings.config
}

/**
 * Actor that performs the transport; it eats all failures and it does not provide any results to the senders.
 * You must mix in the required dependencies or use convenience mix-in such as ``SimpleConfgiruedActorEmail``.
 */
class SimpleEmailActor extends Actor with Emailer {
  this: EmailTransport with EmailConfiguration with MimeMessageBuilder with MimeMessageBodyBuilder with InternetAddressBuilder =>

  def receive = {
    case m: MimeMessage => transportEmailMessage(m).run
    case m: MessageIn => email(m).run
  }

}
