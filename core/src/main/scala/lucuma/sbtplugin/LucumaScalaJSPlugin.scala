// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.*

import Keys.*

object LucumaScalaJSPlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  override def trigger = allRequirements

  override def projectSettings = Seq(
    evictionErrorLevel := Level.Warn
  )

}
