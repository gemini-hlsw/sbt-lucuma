// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt.*
import sbt.*

object LucumaLibPlugin extends AutoPlugin {

  override def requires = TypelevelCiReleasePlugin && LucumaPlugin && LucumaScalafmtPlugin

  override def trigger = allRequirements

  import TypelevelSonatypePlugin.autoImport._

  override def buildSettings = LucumaPlugin.commandAliasSettings(List("mimaReportBinaryIssues"))

}
