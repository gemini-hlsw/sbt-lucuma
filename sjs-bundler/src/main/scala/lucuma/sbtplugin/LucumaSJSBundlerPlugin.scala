// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._, Keys._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import _root_.io.circe.Decoder
import _root_.io.circe.jawn

object LucumaSJSBundlerPlugin extends AutoPlugin {

  override def requires = ScalaJSBundlerPlugin

  override def trigger = allRequirements

  import ScalaJSBundlerPlugin.autoImport._

  override lazy val projectSettings = Seq(
    Compile / npmDependencies ++= {
      val log = sLog.value
      readPackageJson((LocalRootProject / baseDirectory).value, log)(_.dependencies) ++
        readPackageJson(baseDirectory.value, log)(_.dependencies)
    },
    Compile / npmDevDependencies ++= {
      val log = sLog.value
      readPackageJson((LocalRootProject / baseDirectory).value, log)(_.devDependencies) ++
        readPackageJson(baseDirectory.value, log)(_.devDependencies)
    }
  )

  def readPackageJson(baseDirectory: File, log: Logger)(
    f:                               PackageJson => Option[Map[String, String]]
  ) =
    jawn
      .decodeFile[PackageJson](baseDirectory / "package.json")
      .fold(
        e => { log.warn(e.toString); None },
        p => Some(p)
      )
      .flatMap(f)
      .toList
      .flatMap(_.toList)

  case class PackageJson(
    dependencies:    Option[Map[String, String]],
    devDependencies: Option[Map[String, String]]
  )

  object PackageJson {
    implicit def decoder: Decoder[PackageJson] =
      Decoder.forProduct2("dependencies", "devDependencies")(PackageJson(_, _))
  }

}
