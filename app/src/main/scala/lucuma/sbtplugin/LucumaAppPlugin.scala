// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.github.sbt.git.*
import org.typelevel.sbt.*
import org.typelevel.sbt.gha.GitHubActionsPlugin
import sbt.*

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

import Keys.*

object LucumaAppPlugin extends AutoPlugin {

  override def requires = LucumaPlugin && LucumaScalafmtPlugin

  override def trigger = allRequirements

  import SbtGit.GitKeys.*
  import GitHubActionsPlugin.autoImport.*
  import TypelevelCiPlugin.autoImport.*
  import TypelevelKernelPlugin.autoImport.*

  override def buildSettings = versionSettings ++ ciSettings

  override def projectSettings = scalacSettings

  // Inlining across lucuma.** is only safe for applications: libraries would bake their
  // dependencies' code into their own bytecode, breaking binary compatibility.
  private lazy val scalacSettings = Seq(
    scalacOptions ++= {
      if (tlIsScala3.value && githubIsWorkflowBuild.value) Seq("-opt", "-opt-inline:lucuma.**")
      else Seq.empty
    }
  )

  // Settings to use git to define the version of the project
  private def timestamp(d: Date): String = f"$d%tY$d%tm$d%td-$d%tH$d%tM"

  private lazy val versionSettings = Seq(
    version := dateFormatter.format(
      Instant.now.atZone(ZoneId.of("UTC")).toLocalDate
    ) + "-" + gitHeadCommit.value.map(_.take(8)).getOrElse(s"HEAD-${timestamp(new Date)}")
      + (if (gitUncommittedChanges.value) "-UNCOMMITTED" else "")
  )

  private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

  private lazy val ciSettings = Seq(
    tlCiMimaBinaryIssueCheck := false
  )

}
