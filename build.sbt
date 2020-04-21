
inThisBuild(
  (homepage := Some(url("https://github.com/gemini-hlsw/sbt-gsp"))) +: gspPublishSettings
)

addSbtPlugin("de.heikoseeberger"         % "sbt-header"   % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.11")

lazy val sbtGsp = (project in file("."))
  .disablePlugins(GspPlugin)
  .enablePlugins(SbtPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "sbt-gsp",
  )
  .settings(gspHeaderSettings)

