// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit
import com.github.sbt.git.SbtGit.git
import org.scalafmt.sbt.ScalafmtPlugin
import org.typelevel.sbt.*
import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.gha.GitHubActionsPlugin
import org.typelevel.sbt.mergify.MergifyPlugin
import sbt.*
import sbt.Keys.*
import sbtheader.AutomateHeaderPlugin
import sbtheader.HeaderPlugin
import scalafix.sbt.ScalafixPlugin

import scala.concurrent.duration.*

object LucumaPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport._
  import GitHubActionsPlugin.autoImport._
  import HeaderPlugin.autoImport._
  import MergifyPlugin.autoImport._
  import ScalafixPlugin.autoImport._
  import TypelevelCiPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._
  import TypelevelKernelPlugin.autoImport._

  object autoImport {

    lazy val lucumaGlobalSettings = Seq(
      semanticdbEnabled := true,                       // enable SemanticDB
      semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
    )

    lazy val lucumaScalaVersionSettings = Seq(
      crossScalaVersions := Seq("3.9.0"),
      scalaVersion       := crossScalaVersions.value.head
    )

    lazy val lucumaScalacSettings = Seq(
      tlJdkRelease := Some(25)
    )

    lazy val lucumaScalacProjectSettings = Seq(
      // workaround https://github.com/fthomas/refined/pull/1317
      scalacOptions += "-Wconf:msg=Given search preference for .*WitnessAs:s",
      // Move this the sbt-typelevel plugin?
      // sbt-typelevel has better facilities for checking verions, but they are private
      scalacOptions ++= {
        if (scalaVersion.value.startsWith("3"))
          Seq("-Wunused:nowarn")
        else
          Seq.empty
      }
    )

    lazy val lucumaDocSettings = Seq(
      Compile / doc / sources := Seq.empty
    )

    lazy val lucumaHeaderSettings = Seq(
      headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
      headerLicense  := Some(
        HeaderLicense.Custom(
          """|Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
           |For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
           |""".stripMargin
        )
      )
    )

    lazy val lucumaPublishSettings = Seq(
      organization     := "edu.gemini",
      organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
      licenses += (("BSD-3-Clause", url("https://opensource.org/licenses/BSD-3-Clause"))),
      developers       := List(
        Developer("cquiroz", "Carlos Quiroz", "cquiroz@gemini.edu", url("https://www.gemini.edu")),
        Developer("jluhrs", "Javier Lührs", "jluhrs@gemini.edu", url("https://www.gemini.edu")),
        Developer("sraaphorst",
                  "Sebastian Raaphorst",
                  "sraaphorst@gemini.edu",
                  url("https://www.gemini.edu")
        ),
        Developer("swalker2m", "Shane Walker", "swalker@gemini.edu", url("https://www.gemini.edu")),
        Developer("tpolecat", "Rob Norris", "rnorris@gemini.edu", url("https://www.tpolecat.org")),
        Developer("rpiaggio", "Raúl Piaggio", "rpiaggio@gemini.edu", url("https://www.gemini.edu")),
        Developer("toddburnside",
                  "Todd Burnside",
                  "tburnside@gemini.edu",
                  url("https://www.gemini.edu")
        ),
        Developer("hugo-vrijswijk",
                  "Hugo van Rijswijjk",
                  "hugovr@castor-it.nl",
                  url("https://www.gemini.edu")
        )
      )
    )

    lazy val lucumaCiSettings = Seq(
      githubWorkflowJavaVersions   := Seq(JavaSpec.temurin("25")),
      Def.derive(tlFatalWarnings := githubIsWorkflowBuild.value),
      evictionErrorLevel           := {
        if (githubIsWorkflowBuild.value)
          Level.Error // fatal in CI
        else
          Level.Warn  // relaxed locally for snapshot testing, etc.
      },
      mergifyStewardConfig         := Some(
        MergifyStewardConfig(author = "lucuma-steward[bot]", mergeMinors = true)
      ),
      mergifyPrRules ~= {
        _.map { rule =>
          rule.copy(conditions = rule.conditions.map {
            case MergifyCondition.Or(conditions) =>
              MergifyCondition.Or(
                conditions ::: MergifyCondition.Custom("title=flake.lock: Update") :: Nil
              )
            case other                           => other
          })
        }
      },
      tlCiHeaderCheck              := true,
      tlCiScalafmtCheck            := true,
      githubWorkflowBuild          :=
        githubWorkflowBuild.value.map {
          case step: WorkflowStep.Sbt if step.name.exists(_.contains("Check headers")) =>
            WorkflowStep.Sbt(
              commands = step.commands ++
                List("lucumaScalafmtCheck").filter(_ => tlCiScalafmtCheck.value) ++
                List("lucumaScalafixCheck").filter(_ => tlCiScalafixCheck.value),
              step.id,
              step.name,
              step.cond,
              step.env,
              step.params,
              step.timeoutMinutes,
              step.preamble,
              step.continueOnError
            )
          case step                                                                    => step
        },
      tlCiScalafixCheck            := true,
      tlCiDocCheck                 := false, // we are generating empty docs anyway
      tlCiDependencyGraphJob       := false,
      githubWorkflowArtifactUpload := true
    )

    // Dependency downloads in CI fail spuriously (connection resets from Maven Central). Three
    // defenses, all aimed at keeping downloads out of the test steps and surviving them where
    // they must happen:
    //
    // 1. `setup-java` keys its sbt cache on the build files alone, so any other workflow in the
    //    repo that also uses `cache: sbt` (an npm publish, a nightly) races CI to save the key. A
    //    short job that only resolves one project wins that race and saves a partial cache; CI then
    //    gets a cache hit, skips `sbt +update`, and downloads the rest mid-test, exposed to the
    //    network. Hashing ci.yml along with the build files gives CI a key of its own.
    // 2. The `sbt update` step, which runs on a cache miss and does nearly all the downloading, is
    //    retried as a whole, and also fetches the Scala 3 compiler bridge, which sbt otherwise
    //    resolves at the first `compile`. Coursier itself does not retry a connection reset: its
    //    downloader turns the exception into an error value that the retry loop takes as success.
    // 3. Coursier's own retries are raised where they do apply: lm-coursier re-runs a resolution on
    //    "Connection timed out" and HTTP 5xx, and the downloader retries SSL exceptions.
    lazy val lucumaDependencyCacheSettings = Seq(
      // Rewrite the final job list rather than `githubWorkflowJobSetup`: builds that assemble
      // their own setup steps (a custom checkout, say) replace that setting outright and would
      // silently lose the change. This also covers `githubWorkflowAddedJobs`. Jobs that other
      // lucuma plugins append later (they `require` this one, so their settings run after this)
      // must call `withDependencyCacheSteps` themselves; see `LucumaAffectedPlugin.affectedJob`.
      githubWorkflowGeneratedCI := {
        val sbt = ciSbtCommand.value
        githubWorkflowGeneratedCI.value.map(withDependencyCacheSteps(sbt))
      },
      // Append rather than replace: a build may already carry SBT_OPTS (heap, proxies).
      githubWorkflowEnv ~= { env =>
        val retry = s"-D$CoursierDownloadRetryProperty=$CoursierDownloadRetries"
        env.updated("SBT_OPTS", (env.get("SBT_OPTS").toList :+ retry).mkString(" "))
      }
    )

    lazy val lucumaResolutionRetrySettings = Seq(
      csrConfiguration := csrConfiguration.value.withRetry(
        Some((CoursierResolutionRetryDelay, CoursierResolutionRetries))
      )
    )

    lazy val lucumaGitSettings = Seq(
      useConsoleForROGit        := (baseDirectory.value / ".git").isFile,
      git.gitUncommittedChanges := {
        if (githubIsWorkflowBuild.value) {
          git.gitUncommittedChanges.value
        } else {
          import scala.sys.process._
          import scala.util.Try

          Try("git status -s".!!.trim.length > 0).getOrElse(true)
        }
      }
    )

    lazy val lucumaDockerComposeSettings = Seq(
      githubWorkflowBuildPreamble ++= {
        if (hasDockerComposeYml.value)
          Seq(WorkflowStep.Run(List("docker compose up -d"), name = Some("Docker compose up")))
        else Nil
      },
      githubWorkflowBuildPostamble ++= {
        if (hasDockerComposeYml.value)
          Seq(WorkflowStep.Run(List("docker compose down"), name = Some("Docker compose down")))
        else Nil
      }
    )

    lazy val lucumaStewardSettings = Seq(
      GlobalScope / tlCommandAliases += {
        val command =
          List("githubWorkflowGenerate", "+headerCreateAll") ++
            List("lucumaScalafmtGenerate", "+scalafmtAll", "scalafmtSbt")
              .filter(_ => tlCiScalafmtCheck.value) ++
            List("lucumaScalafixGenerate").filter(_ => tlCiScalafixCheck.value)

        "tlPrePrBotHook" -> command
      }
    )

  }

  // setup-java's own default globs for `cache: sbt`, plus the generated workflow. Passing the
  // input replaces the defaults, so they must be repeated here. They come from the `sbt` entry in
  // setup-java's cache.ts (pinned to a release, `main` may have moved):
  // https://github.com/actions/setup-java/blob/v5.7.0/src/cache.ts#L108-L113
  private val SbtCacheDependencyPath: String =
    List(
      "**/*.sbt",
      "**/project/build.properties",
      "**/project/**.scala",
      "**/project/**.sbt",
      ".github/workflows/ci.yml"
    ).mkString("\n")

  private def isSbtCachingSetupJava(step: WorkflowStep.Use): Boolean =
    step.ref match {
      case UseRef.Public("actions", "setup-java", _) => step.params.get("cache").contains("sbt")
      case _                                         => false
    }

  /** The sbt command the generated workflow uses, as sbt-github-actions renders it. */
  private[sbtplugin] val ciSbtCommand: Def.Initialize[String] = Def.setting {
    if (githubWorkflowUseSbtThinClient.value) githubWorkflowSbtCommand.value + " --client"
    else githubWorkflowSbtCommand.value
  }

  /**
   * Gives every sbt-caching `setup-java` step in the job the CI-specific cache key, and turns the
   * generated `sbt update` step into a retried one that also fetches the compiler bridge.
   */
  private[sbtplugin] def withDependencyCacheSteps(sbt: String)(job: WorkflowJob): WorkflowJob =
    job.withSteps(job.steps.map {
      case step: WorkflowStep.Use if isSbtCachingSetupJava(step) =>
        step.updatedParams("cache-dependency-path", SbtCacheDependencyPath)
      case step: WorkflowStep.Sbt if isSbtUpdate(step)           =>
        retriedUpdate(sbt, step)
      case step                                                  => step
    })

  private def isSbtUpdate(step: WorkflowStep.Sbt): Boolean =
    step.commands == List("+update") && step.name.contains("sbt update")

  private val UpdateAttempts: Int     = 3
  private val UpdatePauseSeconds: Int = 30

  // `+scalaCompilerBridgeBinaryJar` resolves the Scala 3 bridge (a no-op on Scala 2), so the
  // first compile later finds it in the cache. The script keeps the step's own shell semantics:
  // a success exits early, the last failure exits non-zero.
  private def retriedUpdate(sbt: String, step: WorkflowStep.Sbt): WorkflowStep.Run =
    WorkflowStep.Run(
      commands = List(
        s"for attempt in ${(1 to UpdateAttempts).mkString(" ")}; do",
        s"  $sbt +update +scalaCompilerBridgeBinaryJar && exit 0",
        s"""  [ "$$attempt" = $UpdateAttempts ] || sleep $UpdatePauseSeconds""",
        "done",
        "exit 1"
      ),
      id = step.id,
      name = step.name,
      cond = step.cond,
      env = step.env,
      params = step.params,
      timeoutMinutes = step.timeoutMinutes,
      continueOnError = step.continueOnError
    )

  private val CoursierResolutionRetries: Int               = 10
  private val CoursierResolutionRetryDelay: FiniteDuration = 5.seconds
  private val CoursierDownloadRetries: Int                 = 10

  // sbt ships a shaded coursier, so the unshaded `coursier.exception-retry` is ignored. This is
  // read as a system property only, hence SBT_OPTS rather than a plain env var.
  private val CoursierDownloadRetryProperty: String =
    "lmcoursier.internal.shaded.coursier.exception-retry"

  private val primaryJavaCond = Def.setting {
    val java = githubWorkflowJavaVersions.value.head
    s"matrix.java == '${java.render}'"
  }

  private val hasDockerComposeYml = Def.setting {
    file("docker-compose.yml").exists()
  }

  import autoImport._

  override def requires =
    TypelevelCiPlugin &&
      TypelevelGitHubPlugin &&
      TypelevelSettingsPlugin &&
      HeaderPlugin &&
      ScalafmtPlugin &&
      LucumaScalafmtPlugin &&
      LucumaScalafixPlugin &&
      GenerativePlugin &&
      GitHubActionsPlugin

  override def trigger: PluginTrigger =
    allRequirements

  override val globalSettings =
    lucumaGlobalSettings

  override val buildSettings =
    lucumaScalaVersionSettings ++
      lucumaScalacSettings ++
      lucumaPublishSettings ++
      lucumaCiSettings ++
      lucumaDependencyCacheSettings ++
      lucumaDockerComposeSettings ++
      lucumaStewardSettings ++
      lucumaGitSettings ++
      commandAliasSettings

  override val projectSettings =
    lucumaDocSettings ++ lucumaHeaderSettings ++ lucumaScalacProjectSettings ++
      lucumaResolutionRetrySettings ++ AutomateHeaderPlugin.projectSettings

  lazy val commandAliasSettings: Seq[Setting[_]] = commandAliasSettings(Nil)

  def commandAliasSettings(extra: List[String]): Seq[Setting[_]] = Seq(
    GlobalScope / tlCommandAliases += {
      val command =
        List(
          "reload",
          "project /",
          "clean",
          "githubWorkflowGenerate",
          "headerCreateAll"
        ) ++
          List("lucumaScalafixGenerate", "scalafixAll").filter(_ => tlCiScalafixCheck.value) ++
          List("lucumaScalafmtGenerate", "scalafmtAll", "scalafmtSbt")
            .filter(_ => tlCiScalafmtCheck.value) ++
          extra

      "prePR" -> command
    }
  )

}
