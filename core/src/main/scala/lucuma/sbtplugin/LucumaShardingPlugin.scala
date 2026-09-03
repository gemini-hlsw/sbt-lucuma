// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt.gha.GenerativePlugin
import sbt.*
import sbttestshards.TestShardsPlugin

/**
 * Splits the CI test run across several jobs.
 *
 * `sbt-test-shards` does the actual splitting: it reads `TEST_SHARD` and `TEST_SHARD_COUNT` and
 * filters each project's test suites down to the ones belonging to this shard. All this plugin does
 * is add the matrix axis and pass those two values in.
 *
 * Off by default. Turn it on with `ThisBuild / lucumaTestShards := 8`.
 */
object LucumaShardingPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport.*

  object autoImport {

    lazy val lucumaTestShards = settingKey[Int](
      "Number of CI jobs to spread the test run over; 0 (the default) means no sharding"
    )
  }

  import autoImport.*

  override def requires: Plugins = LucumaPlugin && TestShardsPlugin

  override def trigger: PluginTrigger = allRequirements

  /** GitHub Actions expression syntax collides with Scala interpolation; build it instead. */
  private def gha(expr: String): String = "$" + s"{{ $expr }}"

  private val ShardKey = "shard"

  private def isTestCommand(command: String): Boolean =
    command == "test" || command.startsWith("lucumaTestAffected")

  override val buildSettings: Seq[Setting[_]] = Seq(
    lucumaTestShards                   := 0,
    githubWorkflowBuildMatrixAdditions ++= {
      val n = lucumaTestShards.value
      if (n > 1) Map(ShardKey -> (0 until n).map(_.toString).toList)
      else Map.empty[String, List[String]]
    },
    githubWorkflowBuild                := {
      val steps = githubWorkflowBuild.value
      val n     = lucumaTestShards.value
      if (n > 1)
        steps.map {
          // matches whether or not LucumaAffectedPlugin has already rewritten the step
          case step: WorkflowStep.Sbt if step.commands.exists(isTestCommand) =>
            step.concatEnv(
              Map(
                "TEST_SHARD"       -> gha(s"matrix.$ShardKey"),
                "TEST_SHARD_COUNT" -> n.toString
              )
            )
          case step                                                             => step
        }
      else steps
    },
    // one slow or flaky shard shouldn't cancel the others
    githubWorkflowBuildMatrixFailFast  := {
      if (lucumaTestShards.value > 1) Some(false) else githubWorkflowBuildMatrixFailFast.value
    }
  )
}
