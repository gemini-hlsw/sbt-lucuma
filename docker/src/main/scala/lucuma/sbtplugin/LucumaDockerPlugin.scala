// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.*
import sbt.Keys.*

import scala.io.Source

object LucumaDockerPlugin extends AutoPlugin {

  override def requires = DockerPlugin && JavaServerAppPackaging

  override def trigger = allRequirements

  override def projectSettings = Seq(
    Docker / daemonUserUid          := Some("3624"),
    Docker / daemonUser             := "software",
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerUpdateLatest              := true,
    dockerUsername                  := Some("noirlab"),
    // No javadocs
    Compile / packageDoc / mappings := Seq(),
    // Don't create launchers for Windows
    makeBatScripts                  := Seq.empty,
    // Launch options
    Universal / javaOptions ++= Seq(
      // -J params will be added as jvm parameters
      // Support remote JMX access
      "-J-Dcom.sun.management.jmxremote",
      "-J-Dcom.sun.management.jmxremote.authenticate=false",
      "-J-Dcom.sun.management.jmxremote.port=2407",
      "-J-Dcom.sun.management.jmxremote.ssl=false",
      // Ensure the locale is correctly set
      "-J-Duser.language=en",
      "-J-Duser.country=US",
      "-J-XX:+HeapDumpOnOutOfMemoryError",
      // Make sure the application exits on OOM
      "-J-XX:+ExitOnOutOfMemoryError",
      "-J-XX:+CrashOnOutOfMemoryError",
      "-J-XX:HeapDumpPath=/tmp",
      "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n"
    ),
    // From https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/customize.html#bash-and-bat-script-extra-defines
    bashScriptExtraDefines ++= // extraLines
      Source
        .fromInputStream(getClass.getResourceAsStream("heroku-docker-set-memory.sh"))
        .getLines
        .toSeq
  )

}
