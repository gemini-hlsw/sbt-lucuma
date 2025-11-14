ThisBuild / tlBaseVersion       := "0.14"
ThisBuild / crossScalaVersions  := Seq("2.12.20")
ThisBuild / tlCiReleaseBranches := Seq("main")

enablePlugins(NoPublishPlugin)

val sbtTypelevelVersion = "0.8.3" // Update in plugins.sbt as well

val scalaJsVersion = "1.20.1"

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma",
    addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.14.4"),
    addSbtPlugin("com.timushev.sbt"   % "sbt-rewarn"               % "0.1.3"),
    addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.5.6"),
    addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.10.0"),
    addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2"),
    addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % scalaJsVersion),
    addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.4.1"),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-ci"         % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-github"     % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-settings"   % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-mergify"    % sbtTypelevelVersion),
    addSbtPlugin("com.armanbilge"     % "sbt-bundlemon"            % "0.1.4"),
    addSbtPlugin("com.timushev.sbt"   % "sbt-updates"              % "0.6.4")
  )

lazy val app = project
  .in(file("app"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-app",
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.1.0")
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
      "io.circe" %% "circe-parser" % "0.14.15"
    ),
    addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1"),
    scriptedLaunchOpts  :=
      scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    Test / test         :=
      scripted.toTask("").value
  )

lazy val css = project
  .in(file("css"))
  .enablePlugins(SbtPlugin)
  .settings(
    name               := "sbt-lucuma-css",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJsVersion),
    scriptedLaunchOpts :=
      scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    Test / test        :=
      scripted.toTask("").value
  )

lazy val jsdom = project
  .in(file("jsdom"))
  .settings(
    name                := "lucuma-jsdom",
    libraryDependencies ++= Seq(
      "org.scala-js"  %% "scalajs-env-jsdom-nodejs" % "1.1.1",
      "org.scala-sbt" %% "io"                       % "1.10.5"
    ),
    tlVersionIntroduced := Map("2.12" -> "0.10.11")
  )

val HerokuAgentVersion = "4.0.4"

lazy val docker = project
  .in(file("docker"))
  .enablePlugins(SbtPlugin, BuildInfoPlugin)
  .settings(
    name                := "sbt-lucuma-docker",
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4"),
    libraryDependencies += // We don't actually use it, but we want bots to update the version.
      "com.heroku.agent" % "heroku-java-metrics-agent" % HerokuAgentVersion % Provided,
    buildInfoKeys       := Seq[BuildInfoKey](
      "HerokuAgentVersion" -> HerokuAgentVersion
    )
  )
  .dependsOn(core)
