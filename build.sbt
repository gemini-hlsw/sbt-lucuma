
inThisBuild(
  (homepage := Some(url("https://github.com/gemini-hlsw/sbt-lucuam"))) +: lucumaPublishSettings
)

addSbtPlugin("de.heikoseeberger"         % "sbt-header"   % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.13")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix" % "0.9.20")

lazy val lucumaGsp = (project in file("."))
  .disablePlugins(LucumaPlugin)
  .enablePlugins(SbtPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "sbt-lucuma",
  )
  .settings(lucumaHeaderSettings)

