package org.eigengo.akkaextras

/**
 * @author janmachacek
 */
package object javamail {

  type EitherFailures[A] = scalaz.EitherT[scalaz.Id.Id, Throwable, A]

}
