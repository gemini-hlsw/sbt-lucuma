// Use this project as its own plugin
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("com.geirsson"              % "sbt-ci-release" % "1.5.7")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"     % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.1.19")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"   % "0.9.29")

