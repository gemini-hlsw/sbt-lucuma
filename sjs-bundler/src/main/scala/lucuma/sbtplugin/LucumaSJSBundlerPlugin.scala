// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import _root_.io.circe.Decoder
import _root_.io.circe.jawn
import sbt._
import sbt.nio.Keys._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin

import scala.collection.immutable.ListSet

import Keys._

object LucumaSJSBundlerPlugin extends AutoPlugin {

  override def requires = ScalaJSBundlerPlugin

  override def trigger = allRequirements

  import ScalaJSBundlerPlugin.autoImport._

  override def buildSettings = Seq(
    monitorPackageJson // this gets the one in project root
  )

  override lazy val projectSettings = Seq(
    Compile / npmDependencies ++= {
      val log = sLog.value
      ListSet( // avoid dupe if base directories are the same
        (LocalRootProject / baseDirectory).value,
        baseDirectory.value
      ).toList.flatMap { baseDirectory =>
        readPackageJson(baseDirectory, log)(_.dependencies)
      }
    },
    Compile / npmDevDependencies ++= {
      val log = sLog.value
      ListSet(
        (LocalRootProject / baseDirectory).value,
        baseDirectory.value
      ).toList.flatMap { baseDirectory =>
        readPackageJson(baseDirectory, log)(_.devDependencies)
      }
    },
    monitorPackageJson
  )

  // monitor package.json for changes, so sbt reloads automatically
  private val monitorPackageJson = Global / checkBuildSources / fileInputs += {
    baseDirectory.value.toGlob / "package.json"
  }

  def readPackageJson(baseDirectory: File, log: Logger)(
    f: PackageJson => Option[Map[String, String]]
  ) = {
    val packageJson = baseDirectory / "package.json"
    if (packageJson.exists()) {
      jawn
        .decodeFile[PackageJson](packageJson)
        .fold(
          e => { log.warn(e.toString); None },
          p => Some(p)
        )
        .flatMap(f)
        .toList
        .flatMap(_.toList)
    } else Nil
  }

  case class PackageJson(
    dependencies:    Option[Map[String, String]],
    devDependencies: Option[Map[String, String]]
  )

  object PackageJson {
    implicit def decoder: Decoder[PackageJson] =
      Decoder.forProduct2("dependencies", "devDependencies")(PackageJson(_, _))
  }

}
