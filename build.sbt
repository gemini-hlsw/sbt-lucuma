// sbt 2 / Scala 3 is a fresh artifact axis: nothing was published for it before 0.17.
ThisBuild / tlBaseVersion       := "0.17"
ThisBuild / tlVersionIntroduced := Map("3" -> "0.17.0")
ThisBuild / crossScalaVersions  := Seq("3.8.3")
ThisBuild / tlCiReleaseBranches := Seq("main")

// plugin behavior is covered by scripted, which `test` does not run
ThisBuild / githubWorkflowBuild +=
  WorkflowStep.Sbt(List("core/scripted", "css/scripted"), name = Some("Scripted tests"))

ThisBuild / resolvers +=
  "gemini-hlsw".at("https://raw.githubusercontent.com/gemini-hlsw/maven-repo/master/releases")

val sbtTypelevelVersion = "0.8-c827b1a-20260910T122817Z-SNAPSHOT" // Update in plugins.sbt as well

val scalaJsVersion = "1.22.0"

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, app, lib, css, docker)

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name                                   := "sbt-lucuma",
    addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.14.8"),
    addSbtPlugin("com.timushev.sbt"   % "sbt-rewarn"               % "0.2.0"),
    addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.6.2"),
    addSbtPlugin("com.github.sbt"     % "sbt-header"               % "5.11.0"),
    addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0"),
    addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % scalaJsVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-ci"         % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-github"     % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-settings"   % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-mergify"    % sbtTypelevelVersion),
    addSbtPlugin("com.timushev.sbt"   % "sbt-updates"              % "0.7.0"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test,
    scriptedLaunchOpts                     :=
      scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  )

lazy val app = project
  .in(file("app"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-app",
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.2.0")
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

lazy val css = project
  .in(file("css"))
  .enablePlugins(SbtPlugin)
  .settings(
    name               := "sbt-lucuma-css",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJsVersion),
    scriptedLaunchOpts :=
      scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  )

val HerokuAgentVersion = "4.0.4"

lazy val docker = project
  .in(file("docker"))
  .enablePlugins(SbtPlugin, BuildInfoPlugin)
  .settings(
    name                := "sbt-lucuma-docker",
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7"),
    libraryDependencies += // We don't actually use it, but we want bots to update the version.
      "com.heroku.agent" % "heroku-java-metrics-agent" % HerokuAgentVersion % Provided,
    buildInfoKeys       := Seq[BuildInfoKey](
      "HerokuAgentVersion" -> HerokuAgentVersion
    )
  )
  .dependsOn(core)
