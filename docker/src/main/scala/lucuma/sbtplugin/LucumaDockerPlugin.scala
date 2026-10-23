// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import buildinfo.BuildInfo.HerokuAgentVersion
import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.CmdLike
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.*
import sbt.Keys.*

import scala.io.Source
import scala.sys.process.*

object LucumaDockerPlugin extends AutoPlugin {

  override def requires = DockerPlugin && JavaServerAppPackaging

  private val HerokuAgentFilename      = s"heroku-java-metrics-agent-${HerokuAgentVersion}.jar"
  // We could use coursier/sbt to download this, but unles we put the time to delve into its API, this is straightforward and works.
  private val HerokuAgentUrl           =
    s"https://repo1.maven.org/maven2/com/heroku/agent/heroku-java-metrics-agent/${HerokuAgentVersion}/${HerokuAgentFilename}"
  private val HerokuAgentTempTargetDir = "heroku-agent-tmp"

  // CVE-2026-91403: https://www.scala-lang.org/blog/jdk-certificate-vulnerability.html
  private val CompromisedCaFingerprints = Seq(
    "42C4ACCD12C4F59597925E1A08A7A90102608543809308D50EC427E118BA1493",
    "45A883F349AEDFD5F3A649B5498FFB121712BA8B448F3F2A91DBC5A766FCDDBE",
    "98B896625C0C230ADEEC3EB2CF6AAD5001ECF9156A68644E82374CE5833BC25B",
    "5F1DED4DD590AF74631290CF073B122A2AAC6A1FA479778A0BD43579BD832699",
    "E7568FE6E12331F4FD7DE61DA4C7559D0F8EB192A218D4741A3BA75184333838",
    "579480AEA166290BD3C643589E795FC0D0E54E86505D180F1DFF99EFD3199512",
    "0164D7CEAE5BB909C272AF151FF44B1177AE9B47D9424B94F1D9558C30934D19",
    "62B7F9868263F72F6911FC78CEAF72C1979158B7368BF648683BB2AA39B02E72",
    "CB6E15165AC802E9B3F993F16C25D41F4E81C21EBDE71AE1B012AC00BF9C7C3F",
    "7485B51C0ED192878FA1D2F948FB529AE1842CC5086C4B67F7BE5B2039DE9489",
    "432EC25F0D28DE9006D97BF752F17BBB2CD41F669933FCB072D9A1148D0261F3",
    "FD9D5C2DBBD4995AB69B8D50C439D9527B00529199C2FD01B2174EB58F07021F",
    "9E39DE2881273ACC225A149C72BA3E0F51424963669AB633EE1DC7DF5DB067B4",
    "A496E23AC0CAB8155FC9B117B97EAE368EFCBB9041AEC8804D5A308D2D01C2D8",
    "4B2460A466F95FD40113BCDCCA2CEB29B3B093921DE45474DA7DF48874C630B1",
    "84112972B8E59D14560AE7E7119654474AAD119990CF3722BFC1E3B91615E1FD"
  )

  // blocked.certs must already exist: the JDK rejects one without its Algorithm header
  private val BlockCompromisedCas = {
    val file = """"$JAVA_HOME/lib/security/blocked.certs""""
    val fps  = CompromisedCaFingerprints.mkString(" ")
    Cmd("RUN", s"test -f $file && { echo; printf '%s\\n' $fps; } >> $file")
  }

  // The last `USER root` is the main stage's (present because we set daemonUserUid),
  // so the JVM refuses these CAs even on unpatched base images
  private def blockCompromisedCas(commands: Seq[CmdLike]): Seq[CmdLike] = {
    val i = commands.lastIndexWhere(_ == Cmd("USER", "root"))
    require(i >= 0, "Cannot find `USER root` in the Dockerfile to block compromised CAs")
    commands.patch(i + 1, Seq(BlockCompromisedCas), 0)
  }

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
    lazy val lucumaDockerUseHerokuAgent =
      settingKey[Boolean]("If true, use the Heroku Java metrics agent (default: true)")
  }

  override def trigger = allRequirements

  import autoImport.*

  override lazy val buildSettings = Seq(
    lucumaDockerDefaultMaxHeap := 512,
    lucumaDockerMinHeap        := 256,
    lucumaDockerHeapPercentMax := 80,
    lucumaDockerHeapSubtract   := 0,
    lucumaDockerOpenDebugPorts := false,
    lucumaDockerUseHerokuAgent := true
  )

  override def projectSettings = Seq(
    dockerBaseImage                 := "eclipse-temurin:25-jre",
    Docker / daemonUserUid          := Some("3624"),
    Docker / daemonUser             := "software",
    dockerCommands                  := blockCompromisedCas(dockerCommands.value),
    // We pass the provenance and output parameters to force Docker to generate a manifest compatible with Heroku.
    // See https://devcenter.heroku.com/articles/container-registry-and-runtime#known-issues-and-limitations
    // and https://stackoverflow.com/questions/79639270/docker-heroku-error-from-registry-unsupported
    // and ultimately https://forums.docker.com/t/force-image-to-use-manifest-media-type-docker-v2-schema-2-instead-of-oci/144974/22
    dockerBuildOptions ++= Seq(
      "--platform",
      "linux/amd64",
      "--provenance",
      "false",
      "--output",
      "type=docker"
    ),
    dockerUpdateLatest              := true,
    dockerUsername                  := Some("noirlab"),
    Docker / mappings               := (Docker / mappings).value ++ {
      if (lucumaDockerUseHerokuAgent.value) {
        val tmpDir: File  = target.value / HerokuAgentTempTargetDir
        tmpDir.mkdirs()
        val tmpFile: File = tmpDir / HerokuAgentFilename
        cleanFiles ++= Seq(tmpFile, tmpDir)
        val status: Int   = url(HerokuAgentUrl) #> tmpFile !

        if (status > 0) throw new RuntimeException(s"Failed to download $HerokuAgentUrl")
        else Seq(tmpFile -> s"${(Docker / defaultLinuxInstallLocation).value}/$HerokuAgentFilename")
      } else Seq.empty
    },
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
    Universal / javaOptions ++= {
      if (lucumaDockerUseHerokuAgent.value)
        Seq(s"-J-javaagent:${(Docker / defaultLinuxInstallLocation).value}/${HerokuAgentFilename}")
      else Seq.empty
    },
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
