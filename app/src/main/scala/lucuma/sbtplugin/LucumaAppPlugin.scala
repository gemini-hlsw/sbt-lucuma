// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt._
import sbt._
import sbtdynver._

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

import Keys._

object LucumaAppPlugin extends AutoPlugin {

  override def requires = LucumaPlugin && LucumaScalafmtPlugin

  override def trigger = allRequirements

  import DynVerPlugin.autoImport._
  import TypelevelCiPlugin.autoImport._

  override def buildSettings = versionSettings ++ ciSettings

  // Settings to use git to define the version of the project

  private lazy val versionSettings = Seq(
    version := dateFormatter.format(
      dynverCurrentDate.value.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
    ) + dynverGitDescribeOutput.value.mkVersion(
      versionFmt,
      fallbackVersion(dynverCurrentDate.value)
    )
  )

  private def versionFmt(out: GitDescribeOutput): String = {
    val dirtySuffix = if (out.dirtySuffix.mkString("", "").nonEmpty) {
      "-UNCOMMITED"
    } else {
      ""
    }
    s"-${out.commitSuffix.sha}$dirtySuffix"
  }

  private def fallbackVersion(d: Date): String = s"HEAD-${DynVer.timestamp(d)}"

  private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

  private lazy val ciSettings = Seq(
    tlCiMimaBinaryIssueCheck := false
  )

}
