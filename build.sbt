ThisBuild / tlBaseVersion       := "0.8"
ThisBuild / crossScalaVersions  := Seq("2.12.16")
ThisBuild / tlCiReleaseBranches := Seq("master")

enablePlugins(NoPublishPlugin)

val sbtTypelevelVersion = "0.4.12"

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma",
    addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.10.1"),
    addSbtPlugin("com.timushev.sbt"   % "sbt-rewarn"               % "0.1.3"),
    addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.4.6"),
    addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.7.0"),
    addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0"),
    addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.10.0"),
    addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.0.0"),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-ci"         % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-github"     % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-settings"   % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-mergify"    % sbtTypelevelVersion)
  )

lazy val app = project
  .in(file("app"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-app",
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
  )
  .dependsOn(core)

lazy val lib = project
  .in(file("lib"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-lib",
    addSbtPlugin("org.typelevel" % "sbt-typelevel-ci-release" % sbtTypelevelVersion)
  )
  .dependsOn(core)

lazy val sjsBundler = project
  .in(file("sjs-bundler"))
  .enablePlugins(SbtPlugin)
  .settings(
    name                := "sbt-lucuma-sjs-bundler",
    tlVersionIntroduced := Map("2.12" -> "0.6.1"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-parser" % "0.14.2"
    ),
    addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0"),
    scriptedLaunchOpts  := {
      scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    Test / test         := {
      scripted.toTask("").value
    }
  )
