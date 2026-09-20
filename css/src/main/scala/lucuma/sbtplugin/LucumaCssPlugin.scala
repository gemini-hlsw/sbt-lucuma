// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.*

import Keys.*

object LucumaCssPlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  object autoImport {
    lazy val lucumaCssExts            = settingKey[Set[String]]("Extensions for CSS files")
    lazy val lucumaCss                = taskKey[Unit]("Copy CSS to target")
    lazy val lucumaCssOutputDirectory =
      settingKey[File]("Directory the CSS files are copied to")
  }
  import autoImport.*
  import ScalaJSPlugin.autoImport.*

  private final val cssDir = "lucuma-css"

  override lazy val buildSettings = Seq(
    lucumaCssExts := Set("css", "scss", "saas")
  )

  override lazy val projectSettings = Seq(
    lucumaCssOutputDirectory := target.value / cssDir,
    Compile / fastLinkJS     := Def.uncached(
      (Compile / fastLinkJS).dependsOn(Compile / lucumaCss).value
    ),
    Compile / fullLinkJS     := Def.uncached(
      (Compile / fullLinkJS).dependsOn(Compile / lucumaCss).value
    ),
    Compile / lucumaCss      := Def.uncached {
      val cache   = streams.value.cacheStoreFactory.make("css")
      val log     = streams.value.log
      val cssExts = lucumaCssExts.value.map("." + _)
      val conv    = fileConverter.value
      val outDir  = lucumaCssOutputDirectory.value

      val files = (Compile / fullClasspath).value.flatMap { attr =>
        val file = conv.toPath(attr.data).toFile
        if (file.getName.endsWith(".jar"))
          List(file)
        else
          IO.listFiles(file / cssDir)
      }.toSet

      def copyJar(file: File): Unit =
        IO.withTemporaryDirectory { tmp =>
          val _ = IO.unzip(
            file,
            tmp,
            name =>
              if (name.startsWith(cssDir) && cssExts.exists(name.endsWith(_))) {
                log.info(s"Copying ${name.split('/').last} from ${file.getName} to $outDir")
                true
              } else false
          )
          // overwrite: unzip keeps the jar entry's timestamp, which is often a fixed epoch,
          // so the default newer-than check would leave stale CSS behind after a version bump.
          if ((tmp / cssDir).exists) IO.copyDirectory(tmp / cssDir, outDir, overwrite = true)
        }

      def copyFile(file: File): Unit = {
        log.info(s"Copying ${file} to $outDir")
        if (file.isDirectory)
          IO.copyDirectory(file, outDir / file.getName)
        else
          IO.copyFile(file, outDir / file.getName)
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
