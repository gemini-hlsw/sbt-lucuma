// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import munit.FunSuite

import AffectedProjects.*

class AffectedProjectsSuite extends FunSuite {

  // A crossProject (shared sources, per-platform base dirs) plus two plain dependents.
  private val modelJvm =
    ProjectInfo("modelJVM",
                Seq("model/shared/src/main/scala", "model/.jvm/src/main/scala", "model/.jvm")
    )
  private val modelJs  =
    ProjectInfo("modelJS",
                Seq("model/shared/src/main/scala", "model/.js/src/main/scala", "model/.js")
    )
  private val app      = ProjectInfo("app", Seq("app/src/main/scala", "app"))
  private val css      = ProjectInfo("css", Seq("css/src/main/scala", "css"))
  private val root     = ProjectInfo("root", Seq.empty)

  private val projects = Seq(modelJvm, modelJs, app, css, root)

  private val dependents = Map(
    "modelJS"  -> Set("app", "css"),
    "modelJVM" -> Set.empty[String],
    "app"      -> Set.empty[String],
    "css"      -> Set.empty[String]
  )

  private val testable = Set("modelJVM", "modelJS", "app", "css")

  private val always = Seq("*.sbt", "project/**", ".github/**")
  private val ignore = Seq("**.md", "docs/**", "**vite.config.*")

  private def run(changed: String*) =
    plan(Some(changed), projects, dependents, testable, always, ignore)

  private def withDefaults(changed: String*) =
    plan(Some(changed), projects, dependents, testable, DefaultAlwaysPaths, DefaultIgnorePaths)

  test("a file under a source directory belongs to that project") {
    assertEquals(run("app/src/main/scala/Main.scala").projects, Seq("app"))
  }

  test("shared crossProject sources belong to every platform") {
    assertEquals(
      run("model/shared/src/main/scala/Model.scala").projects.sorted,
      Seq("app", "css", "modelJS", "modelJVM")
    )
  }

  test("a change reaches transitive dependents, not dependencies") {
    assertEquals(
      run("model/.js/src/main/scala/Js.scala").projects.sorted,
      Seq("app", "css", "modelJS")
    )
  }

  test("a file sbt owns no source directory for still hits the projects below it") {
    // no project claims model/README.txt, but model/.js and model/shared live under model/
    assertEquals(run("model/README.txt").projects.sorted, Seq("app", "css", "modelJS", "modelJVM"))
  }

  test("bundler config runs nothing, at any depth") {
    assertEquals(run("vite.config.ts").projects, Seq.empty[String])
    assertEquals(run("model/vite.config.mts").projects, Seq.empty[String])
  }

  test("a file with no glob and no project still hits the projects below it") {
    // model/notes.txt belongs to no project, but everything under model/ is fair game
    val p = run("model/notes.txt")
    assertEquals(p.all, false)
    assertEquals(p.projects.sorted, Seq("app", "css", "modelJS", "modelJVM"))
  }

  test("aggregators are never scheduled") {
    assert(!run("app/src/main/scala/Main.scala").projects.contains("root"))
  }

  test("ignored paths affect nothing") {
    val p = run("README.md", "docs/guide.md")
    assertEquals(p.all, false)
    assertEquals(p.projects, Seq.empty[String])
  }

  test("build definition changes force a full run") {
    val p = run("app/src/main/scala/Main.scala", "build.sbt")
    assertEquals(p.all, true)
    assertEquals(p.projects.sorted, testable.toSeq.sorted)
  }

  test("project/ and .github/ changes force a full run") {
    assert(run("project/plugins.sbt").all)
    assert(run(".github/workflows/ci.yml").all)
  }

  test("an unattributable file forces a full run") {
    val p = run("resource/thing.json")
    assertEquals(p.all, true)
    assert(p.reason.exists(_.contains("does not belong")))
  }

  test("no diff at all forces a full run") {
    val p = plan(None, projects, dependents, testable, always, ignore)
    assertEquals(p.all, true)
    assertEquals(p.reason, Some("no diff available"))
  }

  // Paths taken from lucuma-apps, lucuma-core and lucuma-odb.
  test("the default globs run everything for build, CI and dependency changes") {
    List(
      "build.sbt",
      "project/plugins.sbt",
      ".jvmopts",
      "flake.lock",
      ".github/workflows/ci.yml",
      ".github/validate-schema.mjs",
      "package.json",
      "package-lock.json",
      "pnpm-lock.yaml",
      ".npmrc",
      "docker-compose.yml"
    ).foreach(f => assert(withDefaults(f).all, f))
  }

  test("the default globs run nothing for docs, editor, bot and bundler config") {
    List(
      "README.md",
      "docs/guide.md",
      "LICENSE",
      ".envrc",
      ".gitattributes",
      ".githooks/pre-commit",
      ".vscode/settings.json",
      ".mergify.yml",
      ".scala-steward.conf",
      ".github/renovate.json",
      ".github/dependabot.yml",
      ".scalafmt.conf",
      ".scalafix-common.conf",
      ".prettierrc",
      ".prettierignore",
      ".stylelintignore",
      ".sopsrc",
      "explore/hasura/user-prefs/config.yaml",
      "explore/hasura/user-prefs/metadata/databases/default/tables/public_exploreChartType.yaml",
      "hasura/config.yaml",
      "vite.config.ts",
      "model/vite.config.mts",
      "app/tailwind.config.js"
    ).foreach { f =>
      val p = withDefaults(f)
      assert(!p.all && p.projects.isEmpty, s"$f -> ${p.reason.getOrElse(p.projects.mkString(","))}")
    }
  }

  test("touches is symmetric in the useful direction only") {
    assert(touches("a/b/c.scala", "a/b"))
    assert(touches("a/x.txt", "a/b")) // dir below the file's parent
    assert(!touches("a/b/c.scala", "z"))
    assert(!touches("ab/c.scala", "a"))
  }
}
