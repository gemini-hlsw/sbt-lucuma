// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._, Keys._
import sbt.nio.Keys._

object LucumaCssPlugin extends AutoPlugin {

  object autoImport {
    lazy val lucumaCssExts = settingKey[Set[String]]("Extensions for CSS files")
    lazy val lucumaCss     = taskKey[Unit]("Copy CSS to target")
  }
  import autoImport._

  private final val cssDir = "lucuma-css"

  override lazy val buildSettings = Seq(
    lucumaCssExts := Set("css", "scss", "saas")
  )

  override lazy val projectSettings = Seq(
    Compile / lucumaCss / fileInputs ++=
      (Compile / resourceDirectories).value.map(_.toGlob / cssDir / "**"),
    Compile / lucumaCss := {
      val cssExts = lucumaCssExts.value
      (Compile / fullClasspath).value.foreach { attr =>
        val file = attr.data
        if (file.getName.endsWith(".jar")) {
          IO.unzip(file,
                   target.value,
                   name => name.startsWith(cssDir) && cssExts.exists(name.endsWith(_))
          )
        } else {
          IO.copyDirectory(file / cssDir, target.value)
        }
      }
      ()
    }
  )

}
