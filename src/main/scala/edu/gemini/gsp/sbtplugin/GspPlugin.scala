// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.gsp.sbtplugin

import sbt._
import sbt.Keys._

import de.heikoseeberger.sbtheader.HeaderPlugin
import _root_.io.github.davidgregory084.TpolecatPlugin

object GspPlugin extends AutoPlugin {

  import HeaderPlugin.autoImport._

  object autoImport {

    lazy val gspGlobalSettings = Seq(
      scalaVersion := "2.12.9",
      crossScalaVersions := Seq(scalaVersion.value, "2.13.0"),
      resolvers += Resolver.sonatypeRepo("public")
    )

    lazy val gspHeaderSettings = Seq(
      headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
      headerLicense  := Some(HeaderLicense.Custom(
        """|Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
           |For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
           |""".stripMargin
      ))
    )

    lazy val gspPublishSettings = Seq(
      organization     := "edu.gemini",
      organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
      startYear        := Some(2019),
      licenses         += (("BSD-3-Clause", new URL("https://opensource.org/licenses/BSD-3-Clause"))),
      developers := List(
        Developer("cquiroz",    "Carlos Quiroz",       "cquiroz@gemini.edu",    url("http://www.gemini.edu"  )),
        Developer("jluhrs",     "Javier Lührs",        "jluhrs@gemini.edu",     url("http://www.gemini.edu"  )),
        Developer("sraaphorst", "Sebastian Raaphorst", "sraaphorst@gemini.edu", url("http://www.gemini.edu"  )),
        Developer("swalker2m",  "Shane Walker",        "swalker@gemini.edu",    url("http://www.gemini.edu"  )),
        Developer("tpolecat",   "Rob Norris",          "rnorris@gemini.edu",    url("http://www.tpolecat.org"))
      )
    )

    lazy val gspScalaJsSettings = Seq(
      scalacOptions ~= (_.filterNot(Set("-Xcheckinit"))),
      scalacOptions += "-P:scalajs:sjsDefinedByDefault"
    )

    lazy val gspCommonSettings =
      gspHeaderSettings

  }

  import autoImport._

  override def requires: Plugins =
    HeaderPlugin && TpolecatPlugin

  override def trigger: PluginTrigger =
    allRequirements

  override val globalSettings =
    gspGlobalSettings

  override val projectSettings =
    inConfig(Compile)(gspCommonSettings) ++
    inConfig(Test   )(gspCommonSettings)

}

