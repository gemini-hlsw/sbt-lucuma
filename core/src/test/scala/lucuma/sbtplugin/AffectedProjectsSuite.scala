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

  // `testable` is supplied by the caller, which excludes pure aggregators but keeps any that
  // define tests of their own; here `root` stands in for the pure kind.
  test("pure aggregators are never scheduled") {
    assert(!run("app/src/main/scala/Main.scala").projects.contains("root"))
    assert(!run("build.sbt").projects.contains("root"))
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

  test("the default globs run nothing for docs, editor and local environment files") {
    List(
      "README.md",
      "docs/guide.md",
      "notes/scratch.txt",
      "LICENSE",
      ".editorconfig",
      ".envrc",
      ".gitattributes",
      ".gitignore",
      ".git-blame-ignore-revs",
      ".githooks/pre-commit",
      ".vscode/settings.json",
      ".idea/modules.xml"
    ).foreach { f =>
      val p = withDefaults(f)
      assert(!p.all && p.projects.isEmpty, s"$f -> ${p.reason.getOrElse(p.projects.mkString(","))}")
    }
  }

  // Deliberately not ignored by default: whether these can affect a build is a per-repository
  // call, so each repo adds its own rather than inheriting ours. Until then they fail safe.
  test("repository-specific config is not ignored out of the box") {
    List(
      ".mergify.yml",
      ".scala-steward.conf",
      ".github/renovate.json",
      ".scalafmt.conf",
      ".prettierrc",
      ".sopsrc",
      "explore/hasura/config.yaml",
      "vite.config.ts"
    ).foreach { f =>
      // some of these fail open via the always-list, others because no project claims them;
      // either way nothing gets skipped
      assertEquals(withDefaults(f).projects.sorted, testable.toSeq.sorted, f)
    }
  }

  test("json escaping survives anything a value could contain") {
    assertEquals(toJsonArray(Seq("a", "b")), """["a","b"]""")
    assertEquals(toJsonArray(Seq.empty[String]), "[]")
    assertEquals(toJsonArray(Seq("""a"b""")), """["a\"b"]""")
    assertEquals(toJsonArray(Seq("""a\b""")), """["a\\b"]""")
    // a newline here would break GITHUB_OUTPUT's key=value framing, not just the JSON
    assertEquals(toJsonArray(Seq("a\nb")), """["a\nb"]""")
    assertEquals(toJsonArray(Seq("a\tb")), """["a\tb"]""")
    // split so the compiler doesn't turn the expected text back into a real control character
    assertEquals(toJsonArray(Seq("a\u0001b")), "[\"a" + "\\" + "u0001b\"]")
  }

  test("touches is symmetric in the useful direction only") {
    assert(touches("a/b/c.scala", "a/b"))
    assert(touches("a/x.txt", "a/b")) // dir below the file's parent
    assert(!touches("a/b/c.scala", "z"))
    assert(!touches("ab/c.scala", "a"))
  }

  //
  // baseRef: what each CI event ends up diffing against
  //

  private def pullRequest(base: String) =
    Map("GITHUB_BASE_REF" -> base, "GITHUB_REF_NAME" -> "42/merge", "GITHUB_REF_TYPE" -> "branch")

  private def push(ref: String, refType: String = "branch", before: String = "abc123") =
    Map(
      "GITHUB_REF_NAME" -> ref,
      "GITHUB_REF_TYPE" -> refType,
      DefaultBranchEnv  -> "main",
      PushBaseEnv       -> before
    )

  test("a pull_request diffs against the branch it merges into") {
    assertEquals(baseRef(pullRequest("main") ++ push("42/merge")), Some("origin/main"))
    // a stacked PR targets its parent branch, not the default one
    assertEquals(baseRef(pullRequest("feature-a")), Some("origin/feature-a"))
  }

  // The whole point: the push run GitHub fires alongside the pull_request run must see the same
  // diff, instead of falling back to "everything" for want of a base ref.
  test("a push to a non-default branch diffs against the default branch") {
    assertEquals(baseRef(push("my-branch")), Some("origin/main"))
    // and not against the branch's previous tip, which would only see the newest commits
    assertEquals(baseRef(push("my-branch", before = "deadbeef")), Some("origin/main"))
  }

  // Tests on the default branch are the backstop for everything the dependency graph can't see.
  test("a push to the default branch falls through to its previous tip") {
    assertEquals(baseRef(push("main")), Some("abc123"))
    // ... and to nothing at all where that is absent, i.e. the build job
    assertEquals(baseRef(push("main") - PushBaseEnv), None)
  }

  // Diffing a tag against the branch it points into yields nothing changed, which would publish
  // an untested build.
  test("a tag is never narrowed") {
    assertEquals(baseRef(push("v1.2.3", refType = "tag") - PushBaseEnv), None)
  }

  test("a branch's first push is not narrowed against all-zeroes") {
    assertEquals(baseRef(push("main", before = "0000000000000000000000000000000000000000")), None)
    assertEquals(baseRef(push("main", before = "")), None)
  }

  test("an explicit base always wins") {
    assertEquals(baseRef(push("my-branch") + (BaseEnv -> "origin/release")), Some("origin/release"))
    assertEquals(baseRef(pullRequest("main") + (BaseEnv -> "HEAD~3")), Some("HEAD~3"))
  }

  test("outside CI there is no base, so everything is affected") {
    assertEquals(baseRef(Map.empty), None)
    // a default branch alone is not enough: without a ref we cannot tell whether we are on it
    assertEquals(baseRef(Map(DefaultBranchEnv -> "main")), None)
    // nor is a ref whose type we cannot confirm is a branch
    assertEquals(baseRef(Map(DefaultBranchEnv -> "main", "GITHUB_REF_NAME" -> "x")), None)
  }

  test("blank env vars are treated as absent, not as valid refs") {
    assertEquals(baseRef(push("my-branch") + (BaseEnv -> "  ")), Some("origin/main"))
    assertEquals(baseRef(push("my-branch") + ("GITHUB_BASE_REF" -> "")), Some("origin/main"))
    assertEquals(baseRef(push("my-branch") + (DefaultBranchEnv -> "")), Some("abc123"))
  }
}
