
lazy val scala12Version = "2.12.8"

inThisBuild(Seq(
  organization     := "edu.gemini",
  organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
  startYear        := Some(2019),
  licenses         += ("BSD-3-Clause", new URL("https://opensource.org/licenses/BSD-3-Clause")),
  homepage         := Some(url("https://github.com/gemini-hlsw/sbt-gsp")),
  developers       := List(
    Developer("cquiroz",    "Carlos Quiroz",       "cquiroz@gemini.edu",    url("http://www.gemini.edu")),
    Developer("jluhrs",     "Javier Lührs",        "jluhrs@gemini.edu",     url("http://www.gemini.edu")),
    Developer("sraaphorst", "Sebastian Raaphorst", "sraaphorst@gemini.edu", url("http://www.gemini.edu")),
    Developer("swalker2m",  "Shane Walker",        "swalker@gemini.edu",    url("http://www.gemini.edu")),
    Developer("tpolecat",   "Rob Norris",          "rnorris@gemini.edu",    url("http://www.tpolecat.org")),
  )
))

lazy val headerSettings = Seq(
  // These sbt-header settings can't be set in ThisBuild for some reason
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
       |For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
       |""".stripMargin
  ))
)

lazy val sbtGsp = (project in file("."))
  .settings(headerSettings)
  .settings(
    name         := "sbt-gsp",
    scalaVersion := scala12Version,
    sbtPlugin    := true
  )
  .enablePlugins(AutomateHeaderPlugin)

