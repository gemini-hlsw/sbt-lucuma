ThisBuild / tlBaseVersion       := "0.6"
ThisBuild / scalaVersion        := "2.12.15"
ThisBuild / tlCiReleaseBranches := Seq("master")

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma"
  )

lazy val app = project
  .in(file("app"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-app"
  )
  .dependsOn(core)

lazy val lib = project
  .in(file("lib"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma-lib"
  )
  .dependsOn(core)
