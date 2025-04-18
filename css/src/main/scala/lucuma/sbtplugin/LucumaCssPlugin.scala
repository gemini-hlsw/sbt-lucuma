// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.*

import Keys.*

object LucumaCssPlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  object autoImport {
    lazy val lucumaCssExts = settingKey[Set[String]]("Extensions for CSS files")
    lazy val lucumaCss     = taskKey[Unit]("Copy CSS to target")
  }
  import autoImport._
  import ScalaJSPlugin.autoImport._

  private final val cssDir = "lucuma-css"

  override lazy val buildSettings = Seq(
    lucumaCssExts := Set("css", "scss", "saas")
  )

  override lazy val projectSettings = Seq(
    Compile / fastLinkJS := (Compile / fastLinkJS).dependsOn(Compile / lucumaCss).value,
    Compile / fullLinkJS := (Compile / fullLinkJS).dependsOn(Compile / lucumaCss).value,
    Compile / lucumaCss  := {
      val cache   = streams.value.cacheStoreFactory.make("css")
      val log     = streams.value.log
      val cssExts = lucumaCssExts.value.map("." + _)

      val files = (Compile / fullClasspath).value.flatMap { attr =>
        val file = attr.data
        if (file.getName.endsWith(".jar"))
          List(file)
        else
          IO.listFiles(file / cssDir)
      }.toSet

      def copyJar(file: File): Unit =
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

      def copyFile(file: File): Unit = {
        log.info(s"Copying ${file} to ${target.value / cssDir}")
        if (file.isDirectory)
          IO.copyDirectory(file, target.value / cssDir / file.getName)
        else
          IO.copyFile(file, target.value / cssDir / file.getName)
      }

      Tracked.diffInputs(cache, FileInfo.lastModified)(files) { report =>
        (report.added ++ report.modified).foreach { file =>
          if (file.getName.endsWith(".jar"))
            copyJar(file)
          else
            copyFile(file)
        }
      }

      ()
    }
  )

}
