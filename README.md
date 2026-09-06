# sbt-lucuma

A collection of sbt plugins for shared build settings across Gemini lucuma projects.

## Artifacts

The plugins are split across several published artifacts. Most projects only need
**one** of `sbt-lucuma-lib` (for published libraries) or `sbt-lucuma-app` (for
applications) — both depend on the core `sbt-lucuma` artifact and pull in its plugins
transitively. The CSS, Docker, and jsdom artifacts are added as needed.

| Artifact | Add with | Provides |
| --- | --- | --- |
| `sbt-lucuma` | _(transitive — pulled by `-lib`/`-app`)_ | `LucumaPlugin`, `LucumaScalaJSPlugin`, `LucumaScalafmtPlugin`, `LucumaScalafixPlugin`, `LucumaBundleMonPlugin` |
| `sbt-lucuma-lib` | `addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % V)` | `LucumaLibPlugin` (+ core) |
| `sbt-lucuma-app` | `addSbtPlugin("edu.gemini" % "sbt-lucuma-app" % V)` | `LucumaAppPlugin` (+ core) |
| `sbt-lucuma-css` | `addSbtPlugin("edu.gemini" % "sbt-lucuma-css" % V)` | `LucumaCssPlugin` |
| `sbt-lucuma-docker` | `addSbtPlugin("edu.gemini" % "sbt-lucuma-docker" % V)` | `LucumaDockerPlugin` (+ core) |
| `lucuma-jsdom` | `libraryDependencies += "edu.gemini" %% "lucuma-jsdom" % V` (in `project/`) | `LucumaJSDOMNodeJSEnv` |

In the tables below, **Activation** is either:

- **Automatic** — the plugin enables itself on every (qualifying) project once it is on
  the build classpath (`trigger = allRequirements`); or
- **Opt-in** — you must enable it explicitly with `.enablePlugins(...)` on a project.

---

## `sbt-lucuma` (core)

### `LucumaPlugin`

**Activation:** Automatic (on all projects, once `-lib` or `-app` is added).

The umbrella plugin tying everything together. It requires the relevant
[sbt-typelevel](https://github.com/typelevel/sbt-typelevel) plugins plus the lucuma
scalafmt/scalafix plugins, and configures sensible defaults across the build:

- **Scala / JDK:** Scala `3.9.0`, `tlJdkRelease := 25`.
- **Publishing:** `edu.gemini` organization, BSD-3-Clause license, developer list.
- **CI:** fatal warnings in CI, `evictionErrorLevel` fatal in CI / relaxed locally,
  header + scalafmt + scalafix checks wired into the workflow, Mergify config, doc/dependency
  jobs disabled.
- **Headers:** BSD-3-Clause C++-style line-comment header, applied automatically
  (`AutomateHeaderPlugin`).
- **Git versioning** and a `prePR` / `tlPrePrBotHook` command alias that regenerates the
  workflow, headers, and scalafmt/scalafix configs.

Selected `autoImport`:

| Key | Description |
| --- | --- |
| `lucumaGlobalSettings`, `lucumaScalaVersionSettings`, `lucumaScalacSettings`, `lucumaScalacProjectSettings`, `lucumaPublishSettings`, `lucumaCiSettings`, `lucumaHeaderSettings`, `lucumaGitSettings`, `lucumaDocSettings`, `lucumaDockerComposeSettings`, `lucumaStewardSettings` | Reusable setting sequences, exposed so individual projects can opt in/out of pieces. |

### `LucumaScalaJSPlugin`

**Activation:** Automatic — requires `ScalaJSPlugin`, so it activates **only on Scala.js
projects** (i.e. the JS side of a crossProject).

- Sets `evictionErrorLevel := Level.Warn` for Scala.js projects.
- **Flaky test handling on Scala.js.** MUnit reads `MUNIT_FLAKY_OK` via `System.getenv`
  at test runtime, but `System.getenv` always returns `null` on Scala.js, so flaky tests
  cannot be honored at runtime there. When `MUNIT_FLAKY_OK=true` is set, this plugin instead
  excludes flaky-tagged MUnit tests on Scala.js at the build level
  (`--exclude-tags=Flaky`, evaluated in the sbt JVM via `sys.env`). The JVM side is
  untouched and keeps MUnit's normal runtime behavior.

### `LucumaScalafmtPlugin`

**Activation:** Automatic.

Manages a shared scalafmt config (`.scalafmt-common.conf`) generated from a resource bundled
in the plugin.

| Task | Description |
| --- | --- |
| `lucumaScalafmtGenerate` | Write the common scalafmt config to the build root. |
| `lucumaScalafmtCheck` | Fail if the on-disk config differs from the bundled one. |

### `LucumaScalafixPlugin`

**Activation:** Automatic.

The scalafix counterpart to the above, managing `.scalafix-common.conf`.

| Task | Description |
| --- | --- |
| `lucumaScalafixGenerate` | Write the common scalafix config to the build root. |
| `lucumaScalafixCheck` | Fail if the on-disk config differs from the bundled one. |

### `LucumaBundleMonPlugin`

**Activation:** Automatic where `BundleMonPlugin` is present (requires `LucumaPlugin` &&
`BundleMonPlugin`).

Adds a "Monitor bundle size" CI step (runs `bundleMon` for the `rootJS` matrix project) and
sets `bundleMonCompression := Brotli`.

### `LucumaAffectedPlugin`

**Activation:** Automatic (requires `LucumaPlugin`). Disable with
`ThisBuild / lucumaAffectedTests := false`.

In CI, runs only the tests a pull request can break.

It finds the changed projects by matching changed files against each project's source and
resource directories, then adds everything that depends on them. The dependency graph comes
from sbt itself, so a new `dependsOn` is picked up with no extra config.

When it can't tell what changed, it runs everything. That happens when:

- a changed file matches `lucumaAffectedAlwaysPaths`: the build (`*.sbt`, `project/**`,
  `flake.*`, `.jvmopts`), CI (`.github/**`), node (`package*.json`, lockfiles, `.npmrc`,
  since Scala.js tests run on it) or `docker-compose.yml`
- a changed file belongs to no project
- there is no base ref to diff against, which is the case for pushes to `main`

Files matching `lucumaAffectedIgnorePaths` are dropped before any of that, so a PR touching only
those runs no tests at all. The defaults are deliberately narrow — docs (`**.md`, `docs/**`,
`notes/**`, `LICENSE`) and editor or environment files (`.editorconfig`, `.envrc`,
`.gitattributes`, `.gitignore`, `.git-blame-ignore-revs`, `.githooks/**`, `.vscode/**`,
`.idea/**`) — because an ignore can't be overridden. Anything else is a judgement call about
your repository, so add it there:

```scala
ThisBuild / lucumaAffectedIgnorePaths ++= Seq("**vite.config.*", "**hasura/**")
```

The workflow file itself doesn't change: the `Test` step calls `lucumaTestAffected` instead of
`test`. No project names appear in it, so adding or renaming projects needs no regeneration.

| Setting | Default | Description |
| --- | --- | --- |
| `lucumaAffectedTests` | `true` | Set to `false` to always run the full suite. |
| `lucumaAffectedAlwaysPaths` | see above | Globs that trigger a full run. |
| `lucumaAffectedIgnorePaths` | see above | Globs that trigger nothing. Checked **before** the always list, so an entry here can't be overridden by one there. |
| `lucumaAffectedBaseRef` | `$LUCUMA_AFFECTED_BASE`, else `origin/$GITHUB_BASE_REF` | What to diff against. `None` runs everything. |

Globs use `java.nio` syntax: `*` stops at `/`, `**` doesn't. So `*.sbt` matches `build.sbt` but
not `core/src/sbt-test/foo/build.sbt`.

| Task | Description |
| --- | --- |
| `lucumaAffectedChangedFiles` | Changed files, including uncommitted and untracked ones. `None` if no diff was possible. |
| `lucumaAffectedProjects` | The projects to test. |
| `lucumaTestAffected` | Runs `Test/test` on them. Limited to the current project's aggregates, so `rootJVM` / `rootJS` still works. |

### Skipping other jobs

Tests aren't the only thing a PR can make pointless. Building and linking an app, publishing an
image, checking a bundle size — none of that needs to happen if the diff can't reach the projects
it's built from.

Name the projects a job consumes and it's skipped when none of them are affected:

```scala
ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("explore-deploy", "Build and deploy Explore", steps, cond = someCond),
  explore_app
)
```

It takes the projects themselves, not their ids, so a rename is a refactor and a typo is a compile
error. For a crossProject, name the platform you mean: `schemas_lib.js`.

`lucumaAffectedJob` adds the dependency and ANDs the condition onto whatever the job already had.
For a single step, or to build the expression yourself, use `lucumaAffectedCond(explore_app)` and
add `lucumaAffectedJobId` to the job's `needs`.

Naming one project covers everything upstream of it, since the condition reads the reverse
dependency closure: gating on `explore_app` also fires for `ui_lib` and `schemas_lib` changes.

Both read `needs.affected.outputs.projects`, published by a generated `affected` job. That job
costs an sbt boot, so it's only generated once something depends on it — adding
`lucumaAffectedJobId` to a `needs` list is what brings it into being.

| Task | Description |
| --- | --- |
| `lucumaAffectedReport` | Log the affected projects, and write `projects` and `all` to `GITHUB_OUTPUT` under Actions. |

Try it locally with `LUCUMA_AFFECTED_BASE=origin/main sbt lucumaAffectedProjects`.

> [!WARNING]
> sbt doesn't know about GraphQL schemas used by codegen, database migrations, or npm
> dependencies. If those live outside the project that uses them, add them to
> `lucumaAffectedAlwaysPaths`.

---

## `sbt-lucuma-lib`

### `LucumaLibPlugin`

**Activation:** Automatic (requires `TypelevelCiReleasePlugin` && `LucumaPlugin` &&
`LucumaScalafmtPlugin`).

For **published libraries**. Pulls in CI release support and extends the `prePR` command
alias with `mimaReportBinaryIssues` so binary-compatibility checks run as part of pre-PR
verification.

---

## `sbt-lucuma-app`

### `LucumaAppPlugin`

**Activation:** Automatic (requires `LucumaPlugin` && `LucumaScalafmtPlugin`).

For **applications** (as opposed to published libraries). Defines a date + git-hash version
scheme (e.g. `20250101-abcdef12`, suffixed `-UNCOMMITTED` when the tree is dirty) and disables
MiMa binary-issue checks (`tlCiMimaBinaryIssueCheck := false`).

---

## `sbt-lucuma-css`

### `LucumaCssPlugin`

**Activation:** Opt-in — `.enablePlugins(LucumaCssPlugin)` on a Scala.js project.

Collects CSS assets (from both the classpath and dependency jars) into the target directory
as part of the linking step, so stylesheets shipped inside lucuma libraries end up alongside
the linked JS.

| Key | Description |
| --- | --- |
| `lucumaCss` (task) | Copy CSS into `target/lucuma-css`; hooked into `fastLinkJS` / `fullLinkJS`. |
| `lucumaCssExts` (setting) | File extensions treated as CSS (default `css`, `scss`, `saas`). |

---

## `sbt-lucuma-docker`

### `LucumaDockerPlugin`

**Activation:** Automatic where `DockerPlugin` && `JavaServerAppPackaging` are enabled.

Opinionated Docker packaging (via sbt-native-packager) for lucuma server applications:

- `eclipse-temurin:25-jre` base image, `noirlab` Docker username, non-root `software` user.
- Heroku-compatible image manifest (`--provenance false --output type=docker`, `linux/amd64`).
- OOM safety JVM options, locale settings, no Windows launchers, no javadocs/sources.
- cgroups-aware heap sizing (via a bundled `docker-set-memory.sh`) and optional Heroku Java
  metrics agent (downloaded at build time).

| Key | Default | Description |
| --- | --- | --- |
| `lucumaDockerDefaultMaxHeap` | `512` | Max heap (MB) when cgroups don't report a limit. |
| `lucumaDockerMinHeap` | `256` | Minimum heap (MB). |
| `lucumaDockerHeapPercentMax` | `80` | % of memory for heap when cgroups report `max`. |
| `lucumaDockerHeapSubtract` | `0` | MB to subtract from the memory limit when sizing heap. |
| `lucumaDockerOpenDebugPorts` | `false` | Open JMX / JDWP debug ports in the start script. |
| `lucumaDockerUseHerokuAgent` | `true` | Bundle and attach the Heroku Java metrics agent. |

---

## `lucuma-jsdom`

### `LucumaJSDOMNodeJSEnv`

Not an sbt plugin — a custom Scala.js `JSEnv` (extending `JSDOMNodeJSEnv`) added as a build
dependency. It injects browser globals (`document`, `window`, `navigator`, `Event`,
`IS_REACT_ACT_ENVIRONMENT`, …) into the Node realm so React component tests can run under
jsdom. Use it by setting:

```scala
jsEnv := new lucuma.LucumaJSDOMNodeJSEnv()
```
