
lazy val scala12Version = "2.12.8"

inThisBuild(Seq(
  organization     := "edu.gemini",
  organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
  startYear        := Some(2019),
  licenses         += (("BSD-3-Clause", new URL("https://opensource.org/licenses/BSD-3-Clause"))),
  homepage         := Some(url("https://github.com/gemini-hlsw/sbt-gsp")),
  developers := List(
    Developer("cquiroz",    "Carlos Quiroz",       "cquiroz@gemini.edu",    url("http://www.gemini.edu"  )),
    Developer("jluhrs",     "Javier Lührs",        "jluhrs@gemini.edu",     url("http://www.gemini.edu"  )),
    Developer("sraaphorst", "Sebastian Raaphorst", "sraaphorst@gemini.edu", url("http://www.gemini.edu"  )),
    Developer("swalker2m",  "Shane Walker",        "swalker@gemini.edu",    url("http://www.gemini.edu"  )),
    Developer("tpolecat",   "Rob Norris",          "rnorris@gemini.edu",    url("http://www.tpolecat.org"))
  )
))

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0")

lazy val sbtGsp = (project in file("."))
  .settings(
    name         := "sbt-gsp",
    scalaVersion := scala12Version,
    sbtPlugin    := true
  )
  .enablePlugins(AutomateHeaderPlugin)

