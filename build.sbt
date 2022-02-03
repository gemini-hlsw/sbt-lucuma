ThisBuild / tlBaseVersion       := "0.6"
ThisBuild / scalaVersion        := "2.12.15"
ThisBuild / tlCiReleaseBranches := Seq("master")

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")
val sbtTypelevelVersion = "0.4.3-32-5fff779-SNAPSHOT"

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma",
    addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.9.34"),
    addSbtPlugin("com.timushev.sbt"   % "sbt-rewarn"               % "0.1.3"),
    addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.4.6"),
    addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.6.0"),
    addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0"),
    addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.8.0"),
    addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "1.9.3"),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-ci"         % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-github"     % sbtTypelevelVersion),
    addSbtPlugin("org.typelevel"      % "sbt-typelevel-settings"   % sbtTypelevelVersion)
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
