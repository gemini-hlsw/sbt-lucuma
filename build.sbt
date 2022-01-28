ThisBuild / tlBaseVersion := "0.5"
ThisBuild / scalaVersion := "2.12.15"

lazy val sbtLucuma = (project in file("."))
  // .disablePlugins(LucumaPlugin)
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-lucuma",
    addSbtPlugin("org.typelevel"    % "sbt-typelevel"  % "0.4.3"),
    addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"   % "0.9.33"),
    addSbtPlugin("com.timushev.sbt" % "sbt-rewarn"     % "0.1.3"),
  )
