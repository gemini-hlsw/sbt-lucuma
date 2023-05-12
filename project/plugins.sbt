// Use this project as its own plugin
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "scala"
Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "resources"
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "lib" / "src" / "main" / "scala"

val sbtTypelevelVersion = "0.4.21"
addSbtPlugin("org.typelevel"     % "sbt-typelevel-settings"   % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"     % "sbt-typelevel-ci-release" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"     % "sbt-typelevel-mergify"    % sbtTypelevelVersion)
addSbtPlugin("de.heikoseeberger" % "sbt-header"               % "5.9.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"             % "2.5.0")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"             % "0.10.4")
addSbtPlugin("com.timushev.sbt"  % "sbt-rewarn"               % "0.1.3")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"            % "2.0.7")
addSbtPlugin("com.armanbilge"    % "sbt-bundlemon"            % "0.1.3")
