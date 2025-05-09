// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt.*

import scala.io.Source

import Keys.*

object LucumaScalafixPlugin extends AutoPlugin {

  object autoImport {
    lazy val lucumaScalafixGenerate = taskKey[Unit]("Generate the common scalafix config")
    lazy val lucumaScalafixCheck    = taskKey[Unit]("Check that common scalafix config is up to date")
  }

  override def trigger = allRequirements

  import autoImport._

  override def projectSettings = Seq(
    lucumaScalafixGenerate := {
      val in = getClass.getResourceAsStream(commonConf)
      try
        IO.transfer(in, (ThisBuild / baseDirectory).value / s".$commonConf")
      finally
        in.close()

    },
    lucumaScalafixCheck := {
      val actual = {
        val src = Source.fromFile((ThisBuild / baseDirectory).value / s".$commonConf")
        try
          src.mkString
        finally
          src.close()
      }

      val expected = {
        val src = Source.fromURL(getClass.getResource(commonConf))
        try
          src.mkString
        finally
          src.close()
      }

      if (actual.mkString != expected.mkString)
        sys.error(
          s"$commonConf does not contain contents that would have been generated by sbt-lucuma; try running lucumaScalafixGenerate"
        )
    }
  )

  private val commonConf = "scalafix-common.conf"

}
