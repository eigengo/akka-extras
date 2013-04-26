import sbt._
import Keys._
import sbtrelease._
import net.virtualvoid.sbt.graph.Plugin._

// to sync this project with IntelliJ, run the sbt-idea plugin with: sbt gen-idea
//
// to set user-specific local properties, just create "~/.sbt/my-settings.sbt", e.g.
// javaOptions += "some cool stuff"
//
// This project allows a local.conf on the classpath (e.g. domain/src/main/resources) to override settings, e.g.
//
// test.db.mongo.hosts { "Sampo.home": 27017 }
// test.db.cassandra.hosts { "Sampo.home": 9160 }
// main.db.mongo.hosts = ${test.db.mongo.hosts}
// main.db.cassandra.hosts = ${test.db.cassandra.hosts}
//
// mkdir -p {domain,core,api,main,test}/src/{main,test}/{java,scala,resources}/org/eigengo/akkapatterns
//
// the following were useful for writing this file
// http://www.scala-sbt.org/release/docs/Getting-Started/Multi-Project.html
// https://github.com/sbt/sbt/blob/0.12.2/main/Build.scala
// https://github.com/akka/akka/blob/master/project/AkkaBuild.scala
object EigengoBuild extends Build {

  override val settings = super.settings ++ Seq(
    organization := "org.eigengo.akka-extras",
    version := "0.1.0",
    scalaVersion := "2.10.1"
  )

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

  def module(dir: String) = Project(id = dir, base = file(dir), settings = defaultSettings)
  import Dependencies._

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
    //libraryDependencies <+= scala_reflect,
    libraryDependencies += specs2 % "test",
    libraryDependencies += dumbster % "test",
    libraryDependencies += akka_testkit % "test",

    publishArtifact in Compile := true
  )

  lazy val main = module("main") dependsOn(apple_push, freemarker_templating, javamail)

  lazy val root = Project(
    id = "parent", 
    base = file("."), 
    settings = defaultSettings ++ ScctPlugin.mergeReportSettings ++ Seq(publishArtifact in Compile := false),
    aggregate = Seq(apple_push, freemarker_templating, javamail) 
  )
  
}

object Publish {

  lazy val settings = Seq(
    crossPaths := false,
    pomExtra := akkaExtrasPomExtra,
    publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
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
  
  val akkaExtrasCredentials = Seq(Credentials(Path.userHome / ".sonatype"))

}

object Dependencies {
  // to help resolve transitive problems, type:
  //   `sbt dependency-graph`
  //   `sbt test:dependency-tree`
  val bad = Seq(
    ExclusionRule(name = "log4j"),
    ExclusionRule(name = "commons-logging"),
    ExclusionRule(organization = "org.slf4j")
  )

  val akka_version    = "2.1.2"
  //val scala_reflect   = scalaVersion("org.scala-lang"    % "scala-reflect"     % _)
  val akka            = "com.typesafe.akka" %% "akka-actor"        % akka_version
  val scalaz_core     = "org.scalaz"        %% "scalaz-core"       % "7.0.0"
  val typesafe_config = "com.typesafe"       % "config"            % "1.0.0"
  val akka_testkit    = "com.typesafe.akka" %% "akka-testkit"      % akka_version
  val specs2          = "org.specs2"        %% "specs2"            % "1.14"
  val mail            = "javax.mail"         % "mail"              % "1.4.2"
  val freemarker      = "org.freemarker"     % "freemarker"        % "2.3.19"
  val dumbster        = "dumbster"           % "dumbster"          % "1.6"

}
