// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt.*
import sbt.*
import com.github.sbt.git.*

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

import Keys.*

object LucumaAppPlugin extends AutoPlugin {

  override def requires = LucumaPlugin && LucumaScalafmtPlugin

  override def trigger = noTrigger

  import SbtGit.GitKeys.*
  import TypelevelCiPlugin.autoImport.*

  override def projectSettings = versionSettings ++ ciSettings

  // Settings to use git to define the version of the project
  private def timestamp(d: Date): String = f"$d%tY$d%tm$d%td-$d%tH$d%tM"

  private lazy val versionSettings = Seq(
    version := dateFormatter.format(
      Instant.now.atZone(ZoneId.of("UTC")).toLocalDate
    ) + gitHeadCommit.value.map(_.take(8)).getOrElse(s"HEAD-${timestamp(new Date)}")
      + (if (gitUncommittedChanges.value) "-UNCOMMITTED" else "")
  )

  private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

  private lazy val ciSettings = Seq(
    tlCiMimaBinaryIssueCheck := false
  )

}
