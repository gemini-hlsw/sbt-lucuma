// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._
import org.typelevel.sbt._
import org.typelevel.sbt.gha._

object LucumaLibPlugin extends AutoPlugin {

  override def requires = LucumaPlugin && LucumaScalafmtPlugin && TypelevelPlugin

  override def trigger = allRequirements

  import GenerativePlugin.autoImport._

  override def buildSettings = Seq(
    githubWorkflowBuild ~= { steps =>
      val scalafmtCheck = WorkflowStep.Sbt(
        List("project /", "lucumaScalafmtCheck"),
        name = Some("Check that common scalafmt config is up to date")
      )
      scalafmtCheck +: steps
    }
  )

}
