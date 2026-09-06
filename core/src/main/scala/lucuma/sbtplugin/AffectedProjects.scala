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
