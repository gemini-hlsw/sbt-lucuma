
lazy val scala12Version = "2.12.8"

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/sbt-gsp"))
))

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0")

lazy val sbtGsp = (project in file("."))
  .settings(
    name         := "sbt-gsp",
    scalaVersion := scala12Version,
    sbtPlugin    := true
  )
  .enablePlugins(AutomateHeaderPlugin)

