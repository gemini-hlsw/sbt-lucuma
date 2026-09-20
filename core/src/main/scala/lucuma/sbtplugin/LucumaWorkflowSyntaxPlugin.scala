// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.gha.WorkflowStep
import sbt.*

/**
 * Rewrites the generated workflow's `sbt` invocations for sbt 2.
 *
 * sbt 2 parses its arguments differently: `sbt a b c` is rejected outright, and `sbt '++ 3' foo
 * --bar` feeds the aggregated project keys to `foo` as arguments instead of running it. Both work
 * when the whole sequence, `++` included, arrives as one `;`-separated argument. sbt-typelevel
 * still renders one shell word per command.
 *
 * This runs after every other lucuma plugin that rewrites the generated CI, because those match on
 * a step's individual commands, which no longer exist once they have been joined.
 */
object LucumaWorkflowSyntaxPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport.*

  override def requires: Plugins =
    LucumaPlugin && LucumaAffectedPlugin && LucumaRequiredChecksPlugin

  override def trigger: PluginTrigger = allRequirements

  override val buildSettings: Seq[Setting[?]] = Seq(
    githubWorkflowGeneratedCI := githubWorkflowGeneratedCI.value.map { job =>
      job.withSteps(job.steps.map(joinCommands(job.sbtStepPreamble)))
    }
  )

  private def joinCommands(preamble: List[String])(step: WorkflowStep): WorkflowStep =
    step match {
      case s: WorkflowStep.Sbt =>
        val commands = (if (s.preamble) preamble else Nil) ::: s.commands.toList
        if (commands.sizeIs <= 1) s
        else copyWithCommands(s, List(commands.mkString("; ")), preamble = false)
      case other               => other
    }

  private def copyWithCommands(
    step:     WorkflowStep.Sbt,
    commands: List[String],
    preamble: Boolean
  ): WorkflowStep.Sbt =
    WorkflowStep.Sbt(
      commands,
      step.id,
      step.name,
      step.cond,
      step.env,
      step.params,
      step.timeoutMinutes,
      preamble,
      step.continueOnError
    )

}
