// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import java.nio.file.FileSystems
import java.nio.file.Paths

/** Pure logic behind `LucumaAffectedPlugin`, free of sbt types so it can be unit tested. */
private[sbtplugin] object AffectedProjects {

  /**
   * A project as far as change detection is concerned: an id and every directory whose contents
   * belong to it (source/resource dirs plus its base directory), all relative to the build root and
   * `/`-separated.
   */
  final case class ProjectInfo(id: String, dirs: Seq[String])

  /** `all` means "could not narrow, run everything"; `reason` says why. */
  final case class Plan(all: Boolean, reason: Option[String], projects: Seq[String])

  /** Changing any of these means we cannot tell what broke, so everything is tested. */
  val DefaultAlwaysPaths: Seq[String] = Seq(
    // the build itself
    "*.sbt",
    "project/**",
    ".jvmopts",
    "flake.nix",
    "flake.lock",
    // CI: workflows, and the scripts they run
    ".github/**",
    // node, which Scala.js tests run on
    "package*.json",
    "pnpm-lock.yaml",
    "pnpm-workspace.yaml",
    ".npmrc",
    "*.lock",
    // services tests talk to
    "docker-compose.yml"
  )

  /**
   * Files that can never affect a build, whatever the repository. Dropped before anything else is
   * considered, so a `lucumaAffectedAlwaysPaths` entry cannot win one back -- which is why nothing
   * repository-specific belongs here. Add those per repository instead.
   */
  val DefaultIgnorePaths: Seq[String] = Seq(
    // docs
    "**.md",
    "docs/**",
    "notes/**",
    "LICENSE",
    // editors and local environment
    ".editorconfig",
    ".envrc",
    ".gitattributes",
    ".gitignore",
    ".git-blame-ignore-revs",
    ".githooks/**",
    ".vscode/**",
    ".idea/**"
  )

  /** Env var holding an explicit base ref, set by hand or by a consuming build. */
  val BaseEnv: String = "LUCUMA_AFFECTED_BASE"

  /**
   * Env var holding the previous tip of the branch being pushed (`github.event.before`). Only
   * consulted on the default branch, where there is no "since we diverged" to measure against.
   */
  val PushBaseEnv: String = "LUCUMA_AFFECTED_PUSH_BASE"

  /** Env var holding the repository's default branch name (`github.event.repository...`). */
  val DefaultBranchEnv: String = "LUCUMA_AFFECTED_DEFAULT_BRANCH"

  /**
   * What to diff against, given the environment. Pure so every case can be pinned down in tests;
   * `None` means "cannot narrow", which the caller turns into a full build.
   *
   * The order is most-specific first:
   *   1. an explicit [[BaseEnv]], which always wins;
   *   1. `GITHUB_BASE_REF`, set only on `pull_request` events, naming the branch being merged into;
   *   1. the default branch, for a push to any *other* branch -- this is what makes a push build
   *      see the same diff its pull_request counterpart sees, instead of falling back to
   *      "everything" because no base ref was supplied;
   *   1. [[PushBaseEnv]], which is how a push to the default branch itself gets a base.
   */
  def baseRef(env: Map[String, String]): Option[String] = {
    def get(key: String): Option[String] = env.get(key).map(_.trim).filter(_.nonEmpty)

    get(BaseEnv)
      .orElse(get("GITHUB_BASE_REF").map("origin/" + _))
      .orElse(defaultBranchBase(get))
      .orElse(get(PushBaseEnv).filter(isCommit))
  }

  /**
   * `origin/<default branch>` when we are on some other branch, so the diff is exactly the commits
   * that branch adds -- `git diff a...b` measures from the merge base, so a stale branch is not
   * charged for what landed on the default branch meanwhile.
   */
  private def defaultBranchBase(get: String => Option[String]): Option[String] =
    get(DefaultBranchEnv)
      .filter { default =>
        // A tag is a release. It points *into* a branch, so diffing against that branch yields
        // nothing changed -- which would publish an untested build. Tags get the full suite. An
        // absent ref type is not assumed to be a branch: we only narrow what we can identify.
        get("GITHUB_REF_TYPE").contains("branch") &&
        // On the default branch there is nothing to diverge from; PushBaseEnv covers that case.
        get("GITHUB_REF_NAME").exists(_ != default)
      }
      .map("origin/" + _)

  /** Actions sends all zeroes for a branch's first push, and nothing for a deleted ref. */
  private def isCommit(sha: String): Boolean = sha.nonEmpty && sha.exists(_ != '0')

  def matches(path: String, glob: String): Boolean =
    FileSystems.getDefault.getPathMatcher("glob:" + glob).matches(Paths.get(path))

  def matchesAny(path: String, globs: Seq[String]): Boolean =
    globs.exists(matches(path, _))

  private def under(path: String, dir: String): Boolean =
    dir.isEmpty || path == dir || path.startsWith(dir + "/")

  /**
   * True if `file` lies under `dir`, or `dir` lies under the directory holding `file`. The second
   * case is what maps a file sbt owns no source directory for -- a README next to a crossProject,
   * say -- onto the projects living below it, instead of silently ignoring it.
   */
  def touches(file: String, dir: String): Boolean = {
    val parent = file.lastIndexOf('/') match {
      case -1 => ""
      case i  => file.substring(0, i)
    }
    under(file, dir) || under(dir, parent)
  }

  /**
   * The projects directly containing the changed files, or a reason why we cannot narrow at all.
   * Fails open: a file matching `always`, or belonging to no project, forces a full build.
   */
  def seeds(
    changed:  Seq[String],
    projects: Seq[ProjectInfo],
    always:   Seq[String],
    ignore:   Seq[String]
  ): Either[String, Set[String]] =
    changed
      .filterNot(matchesAny(_, ignore))
      .foldLeft[Either[String, Set[String]]](Right(Set.empty)) { (acc, file) =>
        acc.flatMap { found =>
          if (matchesAny(file, always))
            Left(s"$file matches lucumaAffectedAlwaysPaths")
          else {
            val hits = projects.filter(_.dirs.exists(touches(file, _))).map(_.id).toSet
            if (hits.isEmpty) Left(s"$file does not belong to any project")
            else Right(found ++ hits)
          }
        }
      }

  /**
   * @param changed
   *   changed files, or `None` when the diff could not be computed
   * @param dependents
   *   project id -> ids that depend on it, transitively
   * @param testable
   *   ids we are willing to run tests on (aggregators excluded)
   */
  def plan(
    changed:    Option[Seq[String]],
    projects:   Seq[ProjectInfo],
    dependents: Map[String, Set[String]],
    testable:   Set[String],
    always:     Seq[String],
    ignore:     Seq[String]
  ): Plan =
    changed.map(seeds(_, projects, always, ignore)) match {
      case None               => Plan(all = true, Some("no diff available"), testable.toSeq.sorted)
      case Some(Left(reason)) => Plan(all = true, Some(reason), testable.toSeq.sorted)
      case Some(Right(s))     =>
        val closure = s.flatMap(id => dependents.getOrElse(id, Set.empty[String]) + id)
        Plan(all = false, None, closure.intersect(testable).toSeq.sorted)
    }

  /**
   * Project ids can't contain anything exotic, but this feeds `GITHUB_OUTPUT`, where a stray
   * newline would corrupt the whole file rather than just the value. So escape properly.
   */
  def toJsonArray(values: Seq[String]): String =
    values.map(quote).mkString("[", ",", "]")

  private def quote(value: String): String = {
    val out = new StringBuilder("\"")
    value.foreach {
      case '"'          => out ++= "\\\""
      case '\\'         => out ++= "\\\\"
      case '\n'         => out ++= "\\n"
      case '\r'         => out ++= "\\r"
      case '\t'         => out ++= "\\t"
      case '\b'         => out ++= "\\b"
      case '\f'         => out ++= "\\f"
      case c if c < ' ' => out ++= "\\u%04x".format(c.toInt)
      case c            => out += c
    }
    out += '"'
    out.result()
  }
}
