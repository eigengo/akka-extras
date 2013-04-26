addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.2.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.7")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.1")

resolvers += "SCCT Snapshots" at "http://mtkopone.github.com/scct/maven-repo"

addSbtPlugin("reaktor" % "sbt-scct" % "0.2-SNAPSHOT") // https://github.com/mtkopone/scct/issues/43
