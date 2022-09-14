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
      val log     = streams.value.log
      val cssExts = lucumaCssExts.value

      IO.delete(target.value / cssDir)

      (Compile / fullClasspath).value.foreach { attr =>
        val file = attr.data
        if (file.getName.endsWith(".jar")) {
          IO.unzip(
            file,
            target.value,
            name =>
              if (name.startsWith(cssDir) && cssExts.exists(name.endsWith(_))) {
                log.info(
                  s"Copying ${name.split('/').last} from ${file.getName} to ${target.value / cssDir}"
                )
                true
              } else false
          )
        } else {
          IO.listFiles(file / cssDir).foreach { f =>
            log.info(s"Copying ${f} to ${target.value / cssDir}")
          }
          IO.copyDirectory(file / cssDir, target.value / cssDir)
        }
      }
      ()
    }
  )

}
