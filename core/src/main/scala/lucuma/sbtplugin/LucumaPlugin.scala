// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._
import sbt.Keys._

import de.heikoseeberger.sbtheader.HeaderPlugin
import scalafix.sbt.ScalafixPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.gha.GitHubActionsPlugin
import org.typelevel.sbt._
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import scoverage.ScoverageSbtPlugin
import scoverage.ScoverageKeys._

object LucumaPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport._
  import GitHubActionsPlugin.autoImport._
  import HeaderPlugin.autoImport._
  import ScalafixPlugin.autoImport._
  import TypelevelKernelPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._

  object autoImport {

    lazy val lucumaGlobalSettings = Seq(
      scalaVersion                                   := "2.13.8",
      resolvers += Resolver.sonatypeRepo("public"),
      semanticdbEnabled                              := true, // enable SemanticDB
      semanticdbVersion                              := scalafixSemanticdb.revision, // use Scalafix compatible version
      scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0" // Include OrganizeImport scalafix
    )

    lazy val lucumaHeaderSettings = Seq(
      headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
      headerLicense  := Some(
        HeaderLicense.Custom(
          """|Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
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
      githubWorkflowBuild        := {
        val scalafmtCheck = WorkflowStep.Sbt(
          List("headerCheckAll",
               "scalafmtCheckAll",
               "project /",
               "scalafmtSbtCheck",
               "lucumaScalafmtCheck"
          ),
          name = Some("Check headers and formatting"),
          cond = Some(primaryJavaCond.value)
        )
        scalafmtCheck +: githubWorkflowBuild.value
      }
    )

    lazy val lucumaCoverageSettings = Seq(
      coverageEnabled              := { // enable in CI, but only for the build job
        githubIsWorkflowBuild.value &&
        Option(System.getenv("GITHUB_JOB")).contains("build") &&
        !tlIsScala3.value
      },
      // can't reuse artifacts b/c need to re-compile without coverage enabled
      githubWorkflowArtifactUpload := false,
      githubWorkflowBuild += WorkflowStep.Sbt(
        List("coverageReport", "coverageAggregate"),
        name = Some("Aggregate coverage reports")
      ),
      githubWorkflowBuildPostamble += WorkflowStep.Run(
        List("bash <(curl -s https://codecov.io/bash)"),
        name = Some("Upload code coverage data")
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
      GenerativePlugin &&
      GitHubActionsPlugin &&
      ScoverageSbtPlugin

  override def trigger: PluginTrigger =
    allRequirements

  override val globalSettings =
    lucumaGlobalSettings

  override val buildSettings =
    lucumaPublishSettings ++ lucumaCiSettings ++ lucumaCoverageSettings ++ lucumaDockerComposeSettings

  override val projectSettings =
    lucumaHeaderSettings ++ AutomateHeaderPlugin.projectSettings

}
