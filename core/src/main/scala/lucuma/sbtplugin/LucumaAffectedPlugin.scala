// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.typelevel.sbt.gha.GenerativePlugin
import sbt.*
import sbt.Keys.*
import sbt.internal.BuildStructure

import java.nio.file.Path
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.util.Try

import AffectedProjects.ProjectInfo

/**
 * Restricts CI test runs to the projects a pull request can actually break.
 *
 * The dependency graph is sbt's own (`buildDependencies`), so nothing has to be mirrored in YAML:
 * changed files are mapped onto projects by source/resource directory, then expanded over the
 * reverse dependency closure. It fails open -- a change we cannot attribute to a project runs
 * everything -- and only narrows when there is a base ref to diff against, so pushes to `main`
 * always get the full suite.
 */
object LucumaAffectedPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport.*

  object autoImport {

    lazy val lucumaAffectedTests = settingKey[Boolean](
      "Restrict CI test runs to the projects affected by the PR diff (default: true)"
    )

    lazy val lucumaAffectedAlwaysPaths = settingKey[Seq[String]](
      "Globs that force a full build when changed"
    )

    lazy val lucumaAffectedIgnorePaths = settingKey[Seq[String]](
      "Globs that never affect any project"
    )

    lazy val lucumaAffectedBaseRef = settingKey[Option[String]](
      "Git ref to diff against; None means everything is affected"
    )

    lazy val lucumaAffectedChangedFiles = taskKey[Option[Seq[String]]](
      "Files changed relative to lucumaAffectedBaseRef; None when we cannot tell"
    )

    lazy val lucumaAffectedProjects = taskKey[Seq[String]](
      "Projects whose tests the current diff can break"
    )

    lazy val lucumaAffectedReport = taskKey[Unit](
      "Log the affected projects, and write them to GITHUB_OUTPUT when running in Actions"
    )

    /**
     * Id of the generated job that publishes the affected set. It is only generated when some other
     * job depends on it, so adding it to a job's `needs` is what brings it into being.
     */
    val lucumaAffectedJobId: String = "affected"

    /**
     * Condition that holds when any of the given projects is affected. The job carrying it must
     * also list `lucumaAffectedJobId` in its `needs`; [[lucumaAffectedJob]] does both.
     *
     * Takes the projects themselves rather than their ids, so a rename is a refactor and a typo is
     * a compile error. For a crossProject, name the platform you mean: `schemas_lib.js`.
     */
    def lucumaAffectedCond(project: Project, more: Project*): String =
      (project +: more)
        .map(p => s"contains(fromJSON(needs.$lucumaAffectedJobId.outputs.projects), '${p.id}')")
        .mkString("(", " || ", ")")

    /**
     * Skips `job` entirely unless one of the given projects is affected -- for work that a diff can
     * only break through those projects, like building or deploying an application.
     */
    def lucumaAffectedJob(job: WorkflowJob, project: Project, more: Project*): WorkflowJob = {
      val cond = lucumaAffectedCond(project, more: _*)
      job
        .withNeeds((job.needs :+ lucumaAffectedJobId).distinct)
        .withCond(Some(job.cond.fold(cond)(existing => s"($existing) && $cond")))
    }
  }

  import autoImport.*

  override def requires: Plugins = LucumaPlugin

  override def trigger: PluginTrigger = allRequirements

  override val buildSettings: Seq[Setting[_]] = Seq(
    lucumaAffectedTests        := true,
    lucumaAffectedAlwaysPaths  := AffectedProjects.DefaultAlwaysPaths,
    lucumaAffectedIgnorePaths  := AffectedProjects.DefaultIgnorePaths,
    lucumaAffectedBaseRef      := sys.env
      .get("LUCUMA_AFFECTED_BASE")
      .filter(_.nonEmpty)
      .orElse(sys.env.get("GITHUB_BASE_REF").filter(_.nonEmpty).map("origin/" + _)),
    lucumaAffectedChangedFiles := {
      val log = streams.value.log
      (ThisBuild / lucumaAffectedBaseRef).value match {
        case None       =>
          log.info("[affected] no base ref; everything is affected")
          None
        case Some(base) =>
          changedFiles((ThisBuild / baseDirectory).value, base, log)
      }
    },
    lucumaAffectedProjects     := planTask.value.projects,
    lucumaAffectedReport       := {
      val result = planTask.value
      streams.value.log.info(s"[affected] projects: ${result.projects.mkString(", ")}")

      sys.env.get("GITHUB_OUTPUT").filter(_.nonEmpty).foreach { out =>
        IO.append(
          file(out),
          List(
            s"all=${result.all}",
            s"projects=${AffectedProjects.toJsonArray(result.projects)}"
          ).mkString("", "\n", "\n")
        )
      }
    },
    commands += testAffected,
    // Rewrite the generated job rather than `githubWorkflowBuild`, because TypelevelCiJSPlugin
    // finds its own insertion point by matching `commands == List("test")`. Renaming the command
    // any earlier makes that match fail and silently drops the scalaJSLink step.
    githubWorkflowGeneratedCI  := {
      val jobs     = githubWorkflowGeneratedCI.value
      val narrowed = if (lucumaAffectedTests.value) jobs.map(narrowTestStep) else jobs
      // The extra job costs an sbt boot, so only generate it once something gates on it -- and
      // never a second time, which would be a duplicate job id and so invalid YAML.
      val wanted   = narrowed.exists(_.needs.contains(lucumaAffectedJobId))
      val exists   = narrowed.exists(_.id == lucumaAffectedJobId)
      if (wanted && !exists) narrowed :+ affectedJob.value else narrowed
    }
  )

  /** The one place the plan is computed; every task and the command go through it. */
  private lazy val planTask: Def.Initialize[Task[AffectedProjects.Plan]] = Def.task {
    val log    = streams.value.log
    val result = plan(state.value, lucumaAffectedChangedFiles.value, log)
    result.reason.foreach(r => log.info(s"[affected] running everything: $r"))
    result
  }

  private val reportStepId = "report"

  /** Publishes the affected set for other jobs to gate on. */
  private lazy val affectedJob: Def.Initialize[WorkflowJob] = Def.setting {
    val report = WorkflowStep.Sbt(
      List("lucumaAffectedReport"),
      name = Some("Compute affected projects"),
      id = Some(reportStepId),
      preamble = false
    )

    WorkflowJob(
      id = lucumaAffectedJobId,
      name = "Affected Projects",
      steps = githubWorkflowJobSetup.value.toList :+ report,
      sbtStepPreamble = Nil,
      oses = githubWorkflowOSes.value.toList.take(1),
      scalas = githubWorkflowScalaVersions.value.toList.take(1),
      javas = githubWorkflowJavaVersions.value.toList.take(1),
      outputs = Map(
        "all"      -> s"steps.$reportStepId.outputs.all",
        "projects" -> s"steps.$reportStepId.outputs.projects"
      ),
      timeoutMinutes = Some(20)
    )
  }

  private def narrowTestStep(job: WorkflowJob): WorkflowJob =
    if (job.id != "build") job
    else
      job.withSteps(job.steps.map {
        case step: WorkflowStep.Sbt if step.commands == List("test") =>
          WorkflowStep.Sbt(
            List("lucumaTestAffected"),
            step.id,
            Some("Test affected projects"),
            step.cond,
            step.env,
            step.params,
            step.timeoutMinutes,
            step.preamble,
            step.continueOnError
          )
        case step                                                    => step
      })

  /**
   * `lucumaTestAffected` -- runs `Test/test` on the affected projects, restricted to the current
   * project's aggregate closure so the `rootJVM` / `rootJS` matrix split still works.
   *
   * The project list comes from the `lucumaAffectedProjects` task rather than being recomputed, so
   * the command can never disagree with the task -- including when a build overrides
   * `lucumaAffectedChangedFiles` to supply the diff itself.
   */
  private def testAffected: Command =
    Command.command("lucumaTestAffected") { st =>
      val (next, projects) = Project.extract(st).runTask(ThisBuild / lucumaAffectedProjects, st)
      val log              = next.log
      val scoped           = projects.filter(aggregateClosure(next))

      if (scoped.isEmpty) {
        log.info("[affected] nothing to test")
        next
      } else {
        log.info(s"[affected] testing: ${scoped.mkString(", ")}")
        scoped.map(id => s"$id/Test/test").mkString("all ", " ", "") :: next
      }
    }

  private def plan(st: State, changed: Option[Seq[String]], log: Logger): AffectedProjects.Plan = {
    val extracted = Project.extract(st)
    val structure = extracted.structure
    val deps      = extracted.get(Global / buildDependencies)

    // project id -> ids that depend on it, transitively
    val dependents: Map[String, Set[String]] =
      deps.classpathTransitive.toSeq
        .flatMap { case (dependent, dependencies) =>
          dependencies.map(d => d.project -> dependent.project)
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toSet }

    // Scheduling an aggregator runs its whole subtree, so skip the ones that are pure grouping.
    // One that owns tests still has to run them, even though doing so over-runs its children.
    val testable = structure.allProjectRefs
      .filter(r => deps.aggregate.getOrElse(r, Nil).isEmpty || hasTestSources(structure, r))
      .map(_.project)
      .toSet

    log.debug(s"[affected] changed files: ${changed.fold("<unknown>")(_.mkString(", "))}")

    AffectedProjects.plan(
      changed,
      projectInfos(st),
      dependents,
      testable,
      extracted.get(ThisBuild / lucumaAffectedAlwaysPaths),
      extracted.get(ThisBuild / lucumaAffectedIgnorePaths)
    )
  }

  private def projectInfos(st: State): Seq[ProjectInfo] = {
    val extracted = Project.extract(st)
    val structure = extracted.structure
    val root      = extracted.get(ThisBuild / baseDirectory).toPath.toAbsolutePath.normalize

    structure.allProjectPairs.map { case (resolved, ref) =>
      val dirs = (sourceDirs(structure, ref) :+ resolved.base.toPath)
        .flatMap(relativize(root, _))
        .filter(_.nonEmpty)
        .distinct
      ProjectInfo(ref.project, dirs)
    }
  }

  /** Cheap stand-in for "defines tests": a Test source directory holding at least one file. */
  private def hasTestSources(structure: BuildStructure, ref: ProjectRef): Boolean =
    (ref / Test / unmanagedSourceDirectories)
      .get(structure.data)
      .toSeq
      .flatten
      .exists(d => d.isDirectory && (PathFinder(d) ** "*").get().exists(_.isFile))

  private def sourceDirs(structure: BuildStructure, ref: ProjectRef): Seq[Path] = {
    val data                                                   = structure.data
    def get(c: ConfigKey, k: SettingKey[Seq[File]]): Seq[File] =
      (ref / c / k).get(data).toSeq.flatten
    Seq(Compile, Test)
      .flatMap(c => get(c, unmanagedSourceDirectories) ++ get(c, unmanagedResourceDirectories))
      .map(_.toPath)
  }

  /**
   * Ids aggregated by the current project, transitively, plus the current project itself. When the
   * session sits on a project that aggregates nothing, everything is in scope.
   */
  private def aggregateClosure(st: State): String => Boolean = {
    val extracted = Project.extract(st)
    val deps      = extracted.get(Global / buildDependencies)
    val current   = extracted.currentRef
    val closure   =
      (deps.aggregateTransitive.getOrElse(current, Nil) :+ current).map(_.project).toSet
    if (closure.size <= 1) _ => true else closure
  }

  private def relativize(root: Path, p: Path): Option[String] = {
    val abs = p.toAbsolutePath.normalize
    if (abs.startsWith(root)) Some(root.relativize(abs).toString.replace('\\', '/'))
    else None
  }

  /** `None` means we could not compute a diff, which the caller turns into a full build. */
  private def changedFiles(root: File, base: String, log: Logger): Option[Seq[String]] = {
    def git(args: String*): Option[Seq[String]] =
      Try(Process("git" +: args, root).lineStream_!(nullLogger).toList).toOption

    git("diff", "--name-only", "--no-renames", s"$base...HEAD") match {
      case None        =>
        log.warn(s"[affected] cannot diff against '$base'; treating everything as affected")
        None
      case Some(files) =>
        // uncommitted work counts too, so this is usable outside CI
        val local     = git("diff", "--name-only", "--no-renames", "HEAD").getOrElse(Nil)
        val untracked = git("ls-files", "--others", "--exclude-standard").getOrElse(Nil)
        Some((files ++ local ++ untracked).filter(_.nonEmpty).distinct)
    }
  }

  private val nullLogger: ProcessLogger = ProcessLogger(_ => (), _ => ())
}
