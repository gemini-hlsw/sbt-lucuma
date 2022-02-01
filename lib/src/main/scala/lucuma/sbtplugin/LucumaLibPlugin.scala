// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._
import org.typelevel.sbt._

object LucumaLibPlugin extends AutoPlugin {

  override def requires = TypelevelCiReleasePlugin && LucumaPlugin && LucumaScalafmtPlugin

  override def trigger = allRequirements

}
