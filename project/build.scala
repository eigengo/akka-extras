import sbt._
import Keys._
import sbtrelease._
import net.virtualvoid.sbt.graph.Plugin._

/*
 * To sync this project with IntelliJ, run the sbt-idea plugin with: ``sbt gen-idea``.
 *
 * To set user-specific local properties, just create "~/.sbt/my-settings.sbt", e.g.
 * ``javaOptions += "some cool stuff"``
 *
 * This project allows a local.conf on the classpath to override settings, e.g.
 *
 * {{{
 * test.db.mongo.hosts { "Sampo.home": 27017 }
 * test.db.cassandra.hosts { "Sampo.home": 9160 }
 * main.db.mongo.hosts = ${test.db.mongo.hosts}
 * main.db.cassandra.hosts = ${test.db.cassandra.hosts}
 * }}}
 * 
 * The following were useful for writing this file
 * http://www.scala-sbt.org/release/docs/Getting-Started/Multi-Project.html
 * https://github.com/sbt/sbt/blob/0.12.2/main/Build.scala
 * https://github.com/akka/akka/blob/master/project/AkkaBuild.scala
 */
object EigengoBuild extends Build {

  /**
   * These are the global settings for the project. We set the ``organisation``, 
   * ``version`` and ``scalaVersion`` SBT settings. 
   */
  override val settings = super.settings ++ Seq(
    organization := "org.eigengo.akka-extras",
    version := "0.1.0",
    scalaVersion := "2.10.1"
  )

  /**
   * This variable holds the settings that will be used in each ``module``. These include the 
   * compiler options (scalac and javac), the threading behaviour and the artefact resolvers.
   * If you have a local/enterprise repository, you will need to add it to the ``resolvers``
   * sequence.
   */
  lazy val defaultSettings = Defaults.defaultSettings ++ Publish.settings ++ graphSettings ++ Seq(
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.6", "-deprecation", "-unchecked"),
    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:unchecked", "-Xlint:deprecation", "-Xlint:-options"),
    // https://github.com/sbt/sbt/issues/702
    javaOptions += "-Djava.util.logging.config.file=logging.properties",
    javaOptions += "-Xmx2G",
    outputStrategy := Some(StdoutOutput),
    fork := true,
    maxErrors := 1,
    resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("releases"),
      Resolver.typesafeRepo("releases"),
      "Spray Releases" at "http://repo.spray.io",
      Resolver.typesafeRepo("snapshots"),
      Resolver.sonatypeRepo("snapshots"),
      "Jasper Community" at "http://jasperreports.sourceforge.net/maven2"
      // resolvers += "neo4j repo" at "http://m2.neo4j.org/content/repositories/releases/"  
    ),
    parallelExecution in Test := false
  ) ++ ScctPlugin.instrumentSettings // ++ ScalastylePlugin.Settings

  /**
   * Returns a module at the given ``dir``, where the ``dir`` is also the module id.
   */
  def module(dir: String) = Project(id = dir, base = file(dir), settings = defaultSettings)
  import Dependencies._

  /*
   * Here, we have all the modules that make up Akka Extras. We use underscore for the variable
   * name and dash for the directory name. So, the ``apple_push`` variable defines a module that
   * lives in the ``apple-push`` directory. 
   * The modules include ``settings``, which usually define the ``libraryDependencies``. The 
   * variables that hold the depndencies themselves are defined further down, in the ``Dependencies``
   * object. (Hence the ``import Dependencies._`` above.)
   * Under each directory, we have the usual Maven-esque structure ``src/main/scala``, 
   * ``src/main/resources``; ``src/test/scala`` and so on.
   */

  lazy val apple_push = module("apple-push") settings(
    libraryDependencies += akka,
    libraryDependencies += specs2 % "test"
  )

  lazy val freemarker_templating = module("freemarker-templating") settings (
    libraryDependencies += freemarker,
    libraryDependencies += specs2 % "test"
  )

  lazy val javamail = module("javamail") settings (
    libraryDependencies += mail,
    libraryDependencies += scalaz_core,
    libraryDependencies += typesafe_config,
    libraryDependencies += akka,
    //libraryDependencies <+= scala_reflect,  // this injects ``scalaVersion``
    libraryDependencies += specs2 % "test",
    libraryDependencies += dumbster % "test",
    libraryDependencies += akka_testkit % "test",

    publishArtifact in Compile := true
  )

  lazy val main = module("main") dependsOn(apple_push, freemarker_templating, javamail)

  /**
   * The ``root`` project is slightly different. It does not get published, because it contains no
   * source code. It is only the holder of all the child projects; they are the only ones that
   * actually matter.
   * Notice, however, that the ``root`` project aggregates the child projects; in other words, when
   * you ask SBT to build the ``root`` project, it builds all child projects.
   */
  lazy val root = Project(
    id = "parent", 
    base = file("."), 
    settings = defaultSettings ++ ScctPlugin.mergeReportSettings ++ Seq(publishArtifact in Compile := false),
    aggregate = Seq(apple_push, freemarker_templating, javamail) 
  )
  
}

/**
 * This object includes the publishing mechanism. We publish to [Sonatype](https://oss.sonatype.org/).
 * The code follows the [Publishing](http://www.scala-sbt.org/release/docs/Detailed-Topics/Publishing)
 * guide, but uses the full-blown ``.scala`` syntax instead of the ``.sbt`` syntax.
 */
object Publish {

  /**
   * This sequence of SBT settings is the equivalent of writing
   * {{{
   * pomExtra  := ...
   * publishTo := ...
   * }}}
   * and others in the ``.sbt`` syntax.
   *
   * To keep things readable, we have pulled out the actual values like ``akkaExtrasPomExtra``, which
   * is a proper variable in the ``Publish`` object; and we refer to it when we construct the SBT
   * settings in this sequence. (Viz ``pomExtra := akkaExtrasPomExtra``.)
   */
  lazy val settings = Seq(
    crossPaths := false,
    pomExtra := akkaExtrasPomExtra,
    publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      // versions that end with ``SNAPSHOT`` go to the Snapshots repository on Sonatype;
      // anything else goes to releases on Sonatype.
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else                             Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= akkaExtrasCredentials,
    organizationName := "Eigengo",
    organizationHomepage := Some(url("http://www.eigengo.com")),
    publishMavenStyle := true,
    // Maven central cannot allow other repos.  
    // TODO - Make sure all artifacts are on central.
    pomIncludeRepository := { x => false }
  )

  /**
   * We construct _proper_ Maven-esque POMs to be able to release on Maven.
   */
  val akkaExtrasPomExtra = (
    <url>http://www.eigengo.org/akka-extras</url>
    <licenses>
      <license>
        <name>BSD-style</name>
        <url>http://www.opensource.org/licenses/bsd-license.php</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:eigengo/scalad.git</url>
      <connection>scm:git:git@github.com:eigengo/scalad.git</connection>
    </scm>
    <developers>
      <developer>
        <id>janmachacek</id>
        <name>Jan Machacek</name>
        <url>http://www.eigengo.org</url>
      </developer>
      <developer>
        <id>anirvanchakraborty</id>
        <name>Anirvan Chakraborty</name>
        <url>http://www.eigengo.org</url>
      </developer>
    </developers>
  )

  /**
   * We load the Sonatype credentials from the ``~/.sonatype`` file. This file must contain
   * four lines in this format:
   * {{{
   * realm=Sonatype Nexus Repository Manager
   * host=oss.sonatype.org
   * user=<Your-Username>
   * password=<Your-Password>
   * }}}
   */  
  val akkaExtrasCredentials = Seq(Credentials(Path.userHome / ".sonatype"))

}

/**
 * This object holds the dependency variables that holds the SBT dependency.
 */
object Dependencies {
  /**
   * These are the _bad_ dependencies, that we do not want to download,
   * even if they are transitive.
   * To find out which ones might be causing problems, type:
   *   `sbt dependency-graph`
   *   `sbt test:dependency-tree`
   */
  val bad = Seq(
    ExclusionRule(name = "log4j"),
    ExclusionRule(name = "commons-logging"),
    ExclusionRule(organization = "org.slf4j")
  )

  /* We are able to define the versions of the components if we use them frequently. */
  val akka_version    = "2.1.2"
  //val scala_reflect   = scalaVersion("org.scala-lang"    % "scala-reflect"     % _)

  /* These are are the usual SBT dependencies. If you are adding a dependency, try
   * to keep the nice column formatting.
   */
  val akka            = "com.typesafe.akka" %% "akka-actor"        % akka_version
  val scalaz_core     = "org.scalaz"        %% "scalaz-core"       % "7.0.0"
  val typesafe_config = "com.typesafe"       % "config"            % "1.0.0"
  val akka_testkit    = "com.typesafe.akka" %% "akka-testkit"      % akka_version
  val specs2          = "org.specs2"        %% "specs2"            % "1.14"
  val mail            = "javax.mail"         % "mail"              % "1.4.2"
  val freemarker      = "org.freemarker"     % "freemarker"        % "2.3.19"
  val dumbster        = "dumbster"           % "dumbster"          % "1.6"

}
