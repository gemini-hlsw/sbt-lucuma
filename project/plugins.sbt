// Use this project as its own plugin
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("com.github.sbt"            % "sbt-ci-release" % "1.5.10")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"     % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.1.20")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"   % "0.9.34")
addSbtPlugin("com.timushev.sbt"          % "sbt-rewarn"     % "0.1.3")
