// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.armanbilge.sbt.BundleMonPlugin
import com.armanbilge.sbt.BundleMonPlugin.autoImport._
import org.typelevel.sbt.gha.GenerativeKeys._
import org.typelevel.sbt.gha.WorkflowStep
import sbt._

object LucumaBundleMonPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = LucumaPlugin && BundleMonPlugin

  override def buildSettings: Seq[Setting[_]] = Seq(
    ThisBuild / githubWorkflowBuild +=
      WorkflowStep.Sbt(
        List("bundleMon"),
        name = Some("Monitor bundle size"),
        cond = Some("matrix.project == 'rootJS'")
      ),
    bundleMonCompression := BundleMonCompression.Brotli
  )

}
