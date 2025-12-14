// Use this project as its own plugin
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "scala"
Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "core" / "src" / "main" / "resources"
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "lib" / "src" / "main" / "scala"

val sbtTypelevelVersion      = "0.8.3" // Update in build.sbt as well
addSbtPlugin("org.typelevel"    % "sbt-typelevel-settings"   % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"    % "sbt-typelevel-ci-release" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"    % "sbt-typelevel-mergify"    % sbtTypelevelVersion)
addSbtPlugin("com.github.sbt"   % "sbt-native-packager"      % "1.11.4")
addSbtPlugin("com.github.sbt"   % "sbt-header"               % "5.11.0")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"             % "2.5.6")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"             % "0.14.5")
addSbtPlugin("com.timushev.sbt" % "sbt-rewarn"               % "0.1.3")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"            % "2.4.3")
addSbtPlugin("com.armanbilge"   % "sbt-bundlemon"            % "0.1.4")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"            % "0.13.1")
