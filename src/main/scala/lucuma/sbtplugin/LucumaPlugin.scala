// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import sbt._
import sbt.Keys._

import de.heikoseeberger.sbtheader.HeaderPlugin
import scalafix.sbt.ScalafixPlugin
import org.typelevel.sbt.gha.GenerativePlugin
import org.typelevel.sbt.TypelevelPlugin

object LucumaPlugin extends AutoPlugin {

  import GenerativePlugin.autoImport._
  import HeaderPlugin.autoImport._
  import ScalafixPlugin.autoImport._

  object autoImport {

    lazy val lucumaGlobalSettings = Seq(
      scalaVersion                                   := "2.13.8",
      resolvers ++= Seq(
        Resolver.sonatypeRepo("public"),
        "Gemini Repository".at(
          "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"
        )
      ),
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
      githubWorkflowJavaVersions := Seq("8", "17").map(JavaSpec.temurin(_))
    )

  }

  import autoImport._

  override def requires = TypelevelPlugin

  override def trigger: PluginTrigger =
    allRequirements

  override val globalSettings =
    lucumaGlobalSettings

  override val buildSettings =
    lucumaPublishSettings ++ lucumaCiSettings

  override val projectSettings =
    lucumaHeaderSettings

}
