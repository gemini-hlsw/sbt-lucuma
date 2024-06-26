// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.github.sbt.git.SbtGit.git
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import org.typelevel.sbt.*
import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.gha.GitHubActionsPlugin
import org.typelevel.sbt.mergify.MergifyPlugin
import sbt.*
import sbt.Keys.*
import scalafix.sbt.ScalafixPlugin
import scoverage.ScoverageKeys.*
import scoverage.ScoverageSbtPlugin

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

    lazy val lucumaCoverage = settingKey[Boolean]("Globally enable/disable coverage (default true)")

    lazy val lucumaGlobalSettings = Seq(
      resolvers += "s01-sonatype-public".at(
        "https://s01.oss.sonatype.org/content/repositories/public/"
      ),
      semanticdbEnabled := true,                       // enable SemanticDB
      semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
    )

    lazy val lucumaScalaVersionSettings = Seq(
      crossScalaVersions := Seq("3.3.3"),
      scalaVersion       := crossScalaVersions.value.head
    )

    lazy val lucumaScalacSettings = Seq(
      tlJdkRelease := Some(17)
    )

    lazy val lucumaDocSettings = Seq(
      Compile / doc / sources := Seq.empty
    )

    lazy val lucumaHeaderSettings = Seq(
      headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
      headerLicense  := Some(
        HeaderLicense.Custom(
          """|Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
           |For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
           |""".stripMargin
        )
      )
    )

    lazy val lucumaPublishSettings = Seq(
      organization     := "edu.gemini",
      organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
      licenses += (("BSD-3-Clause", new URL("https://opensource.org/licenses/BSD-3-Clause"))),
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
      githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
      Def.derive(tlFatalWarnings := githubIsWorkflowBuild.value),
      evictionErrorLevel         := {
        if (githubIsWorkflowBuild.value)
          Level.Error // fatal in CI
        else
          Level.Warn  // relaxed locally for snapshot testing, etc.
      },
      mergifyStewardConfig       := Some(
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
      tlCiHeaderCheck            := true,
      tlCiScalafmtCheck          := true,
      githubWorkflowBuild        := {
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
              step.preamble
            )
          case step                                                                    => step
        }
      },
      tlCiScalafixCheck          := true,
      tlCiDocCheck               := false, // we are generating empty docs anyway
      tlCiDependencyGraphJob     := false
    )

    lazy val lucumaGitSettings = Seq(
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

    @deprecated("Separated into build/project settings", "0.6.1")
    lazy val lucumaCoverageSettings =
      lucumaCoverageProjectSettings ++ lucumaCoverageBuildSettings

    lazy val lucumaCoverageProjectSettings = Seq(
      coverageEnabled := {
        lucumaCoverage.value &&                                  // globally enabled
        githubIsWorkflowBuild.value &&                           // enable in CI
        Option(System.getenv("GITHUB_JOB")).contains("build") && // only for build job
        crossVersion.value == CrossVersion.binary                // Scala.js overrides this to add `_sjs1`
      }
    )

    lazy val lucumaCoverageBuildSettings = Seq(
      lucumaCoverage               := true,
      // can't reuse artifacts b/c need to re-compile without coverage enabled
      githubWorkflowArtifactUpload := !lucumaCoverage.value,
      githubWorkflowBuildPostamble ++= {
        if (lucumaCoverage.value)
          Seq(
            WorkflowStep.Sbt(
              List("coverageReport", "coverageAggregate"),
              name = Some("Aggregate coverage reports")
            ),
            WorkflowStep.Use(
              UseRef.Public(
                "codecov",
                "codecov-action",
                "v4"
              ),
              name = Some("Upload code coverage data")
            )
          )
        else Nil
      }
    )

    lazy val lucumaDockerComposeSettings = Seq(
      githubWorkflowBuildPreamble ++= {
        if (hasDockerComposeYml.value)
          Seq(WorkflowStep.Run(List("docker-compose up -d"), name = Some("Docker compose up")))
        else Nil
      },
      githubWorkflowBuildPostamble ++= {
        if (hasDockerComposeYml.value)
          Seq(WorkflowStep.Run(List("docker-compose down"), name = Some("Docker compose down")))
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
      GitHubActionsPlugin &&
      ScoverageSbtPlugin

  override def trigger: PluginTrigger =
    allRequirements

  override val globalSettings =
    lucumaGlobalSettings

  override val buildSettings =
    lucumaScalaVersionSettings ++
      lucumaScalacSettings ++
      lucumaPublishSettings ++
      lucumaCiSettings ++
      lucumaCoverageBuildSettings ++
      lucumaDockerComposeSettings ++
      lucumaStewardSettings ++
      lucumaGitSettings ++
      commandAliasSettings

  override val projectSettings =
    lucumaDocSettings ++ lucumaHeaderSettings ++ lucumaCoverageProjectSettings ++ AutomateHeaderPlugin.projectSettings

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
