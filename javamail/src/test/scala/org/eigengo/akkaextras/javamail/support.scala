package org.eigengo.akkaextras.javamail

import javax.mail.Session
import java.util.Properties
import com.dumbster.smtp.{SimpleSmtpServer, SmtpMessage}
import org.specs2.mutable.FragmentsBuilder

/**
 * @author janmachacek
 */
trait EmailFragments extends FragmentsBuilder {

  trait TestingEmailConfiguration extends EmailConfiguration {
    import scalaz.syntax.monad._
    def getMailSession = {
      val props = new Properties()
      props.put("mail.smtp.port", "10025")
      Session.getInstance(props).point[EitherFailures]
    }
  }

  def receiveEmails[U](f: => U): List[SmtpMessage] = {
    val server = SimpleSmtpServer.start(10025)
    f
    server.stop()
    import scala.collection.JavaConversions._
    server.getReceivedEmail.asInstanceOf[java.util.Iterator[SmtpMessage]].toList
  }

}
