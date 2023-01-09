// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import com.typesafe.sbt.SbtGit.git
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import org.typelevel.sbt._
import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.gha.GitHubActionsPlugin
import org.typelevel.sbt.mergify.MergifyPlugin
import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin
import scoverage.ScoverageKeys._
import scoverage.ScoverageSbtPlugin

object LucumaPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport._
  import GitHubActionsPlugin.autoImport._
  import HeaderPlugin.autoImport._
  import MergifyPlugin.autoImport._
  import ScalafixPlugin.autoImport._
  import TypelevelCiPlugin.autoImport._
  import TypelevelKernelPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._

  object autoImport {

    lazy val lucumaGlobalSettings = Seq(
      resolvers += "s01-sonatype-public".at(
        "https://s01.oss.sonatype.org/content/repositories/public/"
      ),
      semanticdbEnabled := true,                       // enable SemanticDB
      semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
    )

    lazy val lucumaScalaVersionSettings = Seq(
      crossScalaVersions := Seq("2.13.8"),
      scalaVersion       := crossScalaVersions.value.head
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

    lazy val lucumaScalafixSettings = Seq(
      scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0" // Include OrganizeImport scalafix
    )

    lazy val lucumaPublishSettings = Seq(
      organization     := "edu.gemini",
      organizationName := "Association of Universities for Research in Astronomy, Inc. (AURA)",
      licenses += (("BSD-3-Clause", new URL("https://opensource.org/licenses/BSD-3-Clause"))),
      developers       := List(
        Developer("cquiroz", "Carlos Quiroz", "cquiroz@gemini.edu", url("http://www.gemini.edu")),
        Developer("jluhrs", "Javier Lührs", "jluhrs@gemini.edu", url("http://www.gemini.edu")),
        Developer("sraaphorst",
                  "Sebastian Raaphorst",
                  "sraaphorst@gemini.edu",
                  url("http://www.gemini.edu")
        ),
        Developer("swalker2m", "Shane Walker", "swalker@gemini.edu", url("http://www.gemini.edu")),
        Developer("tpolecat", "Rob Norris", "rnorris@gemini.edu", url("http://www.tpolecat.org")),
        Developer("rpiaggio", "Raúl Piaggio", "rpiaggio@gemini.edu", url("http://www.gemini.edu")),
        Developer("toddburnside",
                  "Todd Burnside",
                  "tburnside@gemini.edu",
                  url("http://www.gemini.edu")
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
      githubWorkflowBuild        := {
        val scalafmtCheck = WorkflowStep.Sbt(
          List("headerCheckAll",
               "scalafmtCheckAll",
               "project /",
               "scalafmtSbtCheck",
               "lucumaScalafmtCheck",
               "lucumaScalafixCheck"
          ),
          name = Some("Check headers and formatting"),
          cond = Some(primaryJavaCond.value)
        )
        scalafmtCheck +: githubWorkflowBuild.value
      },
      tlCiScalafixCheck          := true,
      tlCiDocCheck               := false // we are generating empty docs anyway
    )

    lazy val lucumaGitSettings = Seq(
      // TODO replace with `useConsoleForROGit := true`
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
      coverageEnabled := { // enable in CI, but only for the build job
        (ThisBuild / coverageEnabled).?.value
          .getOrElse(true) && // enables disabling coverage at ThisBuild level
        githubIsWorkflowBuild.value &&
        Option(System.getenv("GITHUB_JOB")).contains("build") &&
        !tlIsScala3.value
      }
    )

    lazy val lucumaCoverageBuildSettings = Seq(
      // can't reuse artifacts b/c need to re-compile without coverage enabled
      githubWorkflowArtifactUpload := false,
      githubWorkflowBuildPostamble ++= Seq(
        WorkflowStep.Sbt(
          List("coverageReport", "coverageAggregate"),
          name = Some("Aggregate coverage reports")
        ),
        WorkflowStep.Run(
          List("bash <(curl -s https://codecov.io/bash)"),
          name = Some("Upload code coverage data")
        )
      )
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

    lazy val lucumaStewardSettings =
      addCommandAlias( // Scala Steward runs this command when creating a PR
        "tlPrePrBotHook",
        "githubWorkflowGenerate; +headerCreateAll; lucumaScalafmtGenerate; lucumaScalafixGenerate; +scalafmtAll; scalafmtSbt"
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
      lucumaScalafixSettings ++
      lucumaPublishSettings ++
      lucumaCiSettings ++
      lucumaCoverageBuildSettings ++
      lucumaDockerComposeSettings ++
      lucumaStewardSettings ++
      lucumaGitSettings

  override val projectSettings =
    lucumaDocSettings ++ lucumaHeaderSettings ++ lucumaCoverageProjectSettings ++ AutomateHeaderPlugin.projectSettings

  lazy val commandAliasSettings: Seq[Setting[_]] = commandAliasSettings(Nil)

  def commandAliasSettings(extra: List[String]): Seq[Setting[_]] =
    addCommandAlias(
      "prePR",
      (List(
        "reload",
        "project /",
        "clean",
        "githubWorkflowGenerate",
        "lucumaScalafmtGenerate",
        "lucumaScalafixGenerate",
        "headerCreateAll",
        "scalafmtAll",
        "scalafmtSbt",
        "scalafixAll"
      ) ::: extra).mkString("; ")
    )

}
