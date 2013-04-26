package org.eigengo.akkaextras.javamail

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.specs2.mutable.SpecificationLike

/**
 * @author janmachacek
 */
class EmailActorSpec extends TestKit(ActorSystem()) with SpecificationLike with EmailFragments {
  val actor = TestActorRef(new SimpleEmailActor with SimpleUnconfiguredEmail with TestingEmailConfiguration)

  "Send simple email" in {
    val subject = "Subject"
    val body = "Body"
    val email = receiveEmails {
      val from = "Jan Machacek <janm@cakesolutions.net>"

      actor ! (from, subject, body, List(from), Nil, Nil)
      // sleep for a bit :(
      Thread.sleep(200)
    }.head

    email.getHeaderValue("Subject") mustEqual subject
  }
}
