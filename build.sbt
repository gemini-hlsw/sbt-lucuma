
inThisBuild(
  (homepage := Some(url("https://github.com/gemini-hlsw/sbt-lucuma"))) +: lucumaPublishSettings
)

lazy val sbtLucuma = (project in file("."))
  .disablePlugins(LucumaPlugin)
  .enablePlugins(SbtPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "sbt-lucuma",
    addSbtPlugin("de.heikoseeberger"         % "sbt-header"             % "5.6.0"),
    addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"           % "0.1.20"),
    addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"           % "0.9.33"),
    addSbtPlugin("com.timushev.sbt"          % "sbt-rewarn"             % "0.1.3"),
    addSbtPlugin("io.chrisdavenport"         % "sbt-mima-version-check" % "0.1.2"),
  )
  .settings(lucumaHeaderSettings)

