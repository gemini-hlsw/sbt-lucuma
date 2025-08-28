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

  object autoImport {
    lazy val lucumaDockerDefaultMaxHeap =
      settingKey[Int](
        "Default max heap size in MB when not reported by cgroups (default: 512)"
      )
    lazy val lucumaDockerMinHeap        =
      settingKey[Int]("Minimum heap size in MB (default: 256)")
    lazy val lucumaDockerHeapPercentMax =
      settingKey[Int](
        "Percentage of memory to use for heap when cgroups report 'max' (default: 80)"
      )
    lazy val lucumaDockerHeapSubtract   =
      settingKey[Int](
        "Amount in MB to subtract from memory limit when calculating heap size (default: 0) - Ignored when cgroups report 'max'"
      )
    lazy val lucumaDockerOpenDebugPorts =
      settingKey[Boolean](
        "If true, open debug ports in the JVM in the start script (default: false)"
      )
  }

  override def trigger = allRequirements

  import autoImport.*

  override lazy val buildSettings = Seq(
    lucumaDockerDefaultMaxHeap := 512,
    lucumaDockerMinHeap        := 256,
    lucumaDockerHeapPercentMax := 80,
    lucumaDockerHeapSubtract   := 0,
    lucumaDockerOpenDebugPorts := false
  )

  override def projectSettings = Seq(
    dockerBaseImage                 := "eclipse-temurin:21-jre",
    Docker / daemonUserUid          := Some("3624"),
    Docker / daemonUser             := "software",
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerUpdateLatest              := true,
    dockerUsername                  := Some("noirlab"),
    // No javadocs
    Compile / packageDoc / mappings := Seq(),
    // Omit sources
    Compile / doc / sources         := Seq.empty,
    // Don't create launchers for Windows
    makeBatScripts                  := Seq.empty,
    // Launch options
    Universal / javaOptions ++= Seq(
      // -J params will be added as jvm parameters
      // Ensure the locale is correctly set
      "-J-Duser.language=en",
      "-J-Duser.country=US",
      "-J-XX:+HeapDumpOnOutOfMemoryError",
      // Make sure the application exits on OOM
      "-J-XX:+ExitOnOutOfMemoryError",
      "-J-XX:+CrashOnOutOfMemoryError",
      "-J-XX:HeapDumpPath=/tmp"
    ),
    // Optionally open debug ports
    Universal / javaOptions ++= {
      if (lucumaDockerOpenDebugPorts.value)
        Seq(
          // Support remote JMX access
          "-J-Dcom.sun.management.jmxremote",
          "-J-Dcom.sun.management.jmxremote.authenticate=false",
          "-J-Dcom.sun.management.jmxremote.port=2407",
          "-J-Dcom.sun.management.jmxremote.ssl=false",
          // Support remote debugging
          "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n"
        )
      else Seq.empty
    },
    // From https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/customize.html#bash-and-bat-script-extra-defines
    bashScriptExtraDefines ++=
      Seq(
        s"DEFAULT_MAX_HEAP_MB=${lucumaDockerDefaultMaxHeap.value}",
        s"MIN_HEAP_MB=${lucumaDockerMinHeap.value}",
        s"HEAP_PERCENT_MAX=${lucumaDockerHeapPercentMax.value}",
        s"HEAP_SUBTRACT_MB=${lucumaDockerHeapSubtract.value}"
      ) ++
        Source
          .fromInputStream(getClass.getResourceAsStream("docker-set-memory.sh"))
          .getLines
          .toSeq
  )

}
