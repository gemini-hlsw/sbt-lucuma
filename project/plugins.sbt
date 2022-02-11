// Use this project as its own plugin
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "scala"
Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "resources"
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "lib" / "src" / "main" / "scala"

val sbtTypelevelVersion = "0.4.5"
addSbtPlugin("org.typelevel"     % "sbt-typelevel-settings"   % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"     % "sbt-typelevel-ci-release" % sbtTypelevelVersion)
addSbtPlugin("de.heikoseeberger" % "sbt-header"               % "5.6.5")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"             % "2.4.6")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"             % "0.9.33")
addSbtPlugin("com.timushev.sbt"  % "sbt-rewarn"               % "0.1.3")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"            % "1.9.3")
