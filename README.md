# sbt-lucuma

A collection of sbt plugins for shared build settings across Gemini lucuma projects.

**Requires sbt 2.x** (2.0.8 or later). Since 0.17 these are sbt 2 plugins, published as
`_sbt2_3`; the 0.16 line remains the last one for sbt 1. To port an existing project, see
[docs/sbt-2-migration.md](docs/sbt-2-migration.md).

## Artifacts

The plugins are split across several published artifacts. Most projects only need
**one** of `sbt-lucuma-lib` (for published libraries) or `sbt-lucuma-app` (for
applications) — both depend on the core `sbt-lucuma` artifact and pull in its plugins
transitively. The CSS and Docker artifacts are added as needed.

| Artifact            | Add with                                                                    | Provides                                                                                                       |
| ------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `sbt-lucuma`        | _(transitive — pulled by `-lib`/`-app`)_                                    | `LucumaPlugin`, `LucumaScalaJSPlugin`, `LucumaScalafmtPlugin`, `LucumaScalafixPlugin`, `LucumaWorkflowSyntaxPlugin` |
| `sbt-lucuma-lib`    | `addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % V)`                         | `LucumaLibPlugin` (+ core)                                                                                     |
| `sbt-lucuma-app`    | `addSbtPlugin("edu.gemini" % "sbt-lucuma-app" % V)`                         | `LucumaAppPlugin` (+ core)                                                                                     |
| `sbt-lucuma-css`    | `addSbtPlugin("edu.gemini" % "sbt-lucuma-css" % V)`                         | `LucumaCssPlugin`                                                                                              |
| `sbt-lucuma-docker` | `addSbtPlugin("edu.gemini" % "sbt-lucuma-docker" % V)`                      | `LucumaDockerPlugin` (+ core)                                                                                  |

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
- **Dependency downloads in CI:** the sbt cache key includes `ci.yml`, so another workflow in the
  repo using `cache: sbt` (an npm publish, a nightly) cannot save a partial cache under CI's key
  and leave CI downloading mid-test; other workflows read the cache as shown below. Coursier
  retries harder before failing: 10 resolution attempts 5s apart (`csrConfiguration`) and 10
  download attempts (`SBT_OPTS` in the workflow).
- **Headers:** BSD-3-Clause C++-style line-comment header, applied automatically
  (`AutomateHeaderPlugin`).
- **Git versioning** and a `prePR` / `tlPrePrBotHook` command alias that regenerates the
  workflow, headers, and scalafmt/scalafix configs.

Selected `autoImport`:

| Key                                                                                                                                                                                                                                                                                | Description                                                                          |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `lucumaGlobalSettings`, `lucumaScalaVersionSettings`, `lucumaScalacSettings`, `lucumaScalacProjectSettings`, `lucumaPublishSettings`, `lucumaCiSettings`, `lucumaHeaderSettings`, `lucumaGitSettings`, `lucumaDocSettings`, `lucumaDockerComposeSettings`, `lucumaStewardSettings` | Reusable setting sequences, exposed so individual projects can opt in/out of pieces. |

#### Reading the sbt cache from another workflow

`ci.yml` is the only workflow that should write the sbt dependency cache. A hand-written
workflow (an npm publish, a nightly) that also uses `setup-java`'s `cache: sbt` races CI to
save the key, and a short job that resolves one project saves a partial cache. `setup-java` has
no read-only mode, so drop `cache: sbt` there and restore CI's cache with
`actions/cache/restore`, which never saves:

```yaml
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25
          # no `cache: sbt` here

      - name: Restore sbt cache (read-only, written by ci.yml)
        uses: actions/cache/restore@v4
        with:
          # Must be byte-for-byte what setup-java v5 passes for `cache: sbt` on Linux, in this
          # order: the cache "version" is a hash of this list, and a different list never matches.
          path: |
            /home/runner/.ivy2/cache
            /home/runner/.sbt
            /home/runner/.cache/coursier
            !/home/runner/.sbt/*.lock
            !/home/runner/**/ivydata-*.properties
          key: setup-java-${{ runner.os }}-x64-sbt-${{ hashFiles('**/*.sbt', '**/project/build.properties', '**/project/**.scala', '**/project/**.sbt', '.github/workflows/ci.yml') }}
          restore-keys: setup-java-${{ runner.os }}-x64-sbt-
```

`key` reproduces CI's exact key (same globs as the generated `cache-dependency-path`; `x64` is
spelled the way `setup-java` writes it, not `runner.arch`'s `X64`). Check it once against the
`Cache saved with the key:` line in a CI log. `restore-keys` covers the window where CI is still
saving for the same commit, by taking the newest `setup-java` sbt cache visible to the branch.
Until every hand-written workflow in the repo has been converted, that fallback can also pick a
partial cache one of them saved, so convert them all. Anything the job needs beyond the
restored cache downloads normally and is not saved.

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

| Task                     | Description                                              |
| ------------------------ | -------------------------------------------------------- |
| `lucumaScalafmtGenerate` | Write the common scalafmt config to the build root.      |
| `lucumaScalafmtCheck`    | Fail if the on-disk config differs from the bundled one. |

### `LucumaScalafixPlugin`

**Activation:** Automatic.

The scalafix counterpart to the above, managing `.scalafix-common.conf`.

| Task                     | Description                                              |
| ------------------------ | -------------------------------------------------------- |
| `lucumaScalafixGenerate` | Write the common scalafix config to the build root.      |
| `lucumaScalafixCheck`    | Fail if the on-disk config differs from the bundled one. |

### `LucumaWorkflowSyntaxPlugin`

**Activation:** Automatic.

Rewrites the `sbt` invocations in the generated workflow so they parse under sbt 2, which
rejects `sbt a b c` and mis-reads `sbt '++ 3' foo --bar`. Each step becomes a single
`;`-separated argument with the `++` folded in, and the broken `scalafixAll --check` step
becomes `scalafix --check; Test/scalafix --check`.

It runs after every other lucuma plugin that rewrites the generated CI, because those match
on a step's individual commands, which no longer exist once they have been joined.

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
- there is no base ref to diff against

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

| Setting                     | Default                                                                                     | Description                                                                                                        |
| --------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `lucumaAffectedTests`       | `true`                                                                                      | Set to `false` to always run the full suite.                                                                       |
| `lucumaAffectedAlwaysPaths` | see above                                                                                   | Globs that trigger a full run.                                                                                     |
| `lucumaAffectedIgnorePaths` | see above                                                                                   | Globs that trigger nothing. Checked **before** the always list, so an entry here can't be overridden by one there. |
| `lucumaAffectedBaseRef`     | `$LUCUMA_AFFECTED_BASE`, else `origin/$GITHUB_BASE_REF` on a PR, else `origin/<default branch>` on a push to any other branch, else the previous commit. Never set on a tag. | What to diff against. `None` runs everything.                                                                      |

Globs use `java.nio` syntax: `*` stops at `/`, `**` doesn't. So `*.sbt` matches `build.sbt` but
not `core/src/sbt-test/foo/build.sbt`.

| Task                         | Description                                                                                                 |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `lucumaAffectedChangedFiles` | Changed files, including uncommitted and untracked ones. `None` if no diff was possible.                    |
| `lucumaAffectedProjects`     | The projects to test.                                                                                       |
| `lucumaTestAffected`         | Runs `Test/test` on them. Limited to the current project's aggregates, so `rootJVM` / `rootJS` still works. |

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

GitHub fires CI twice for a commit on a branch with an open PR, once for each event, so what a push
run diffs against matters as much as a PR run does:

- **Push to any non-default branch.** Both the `affected` job and `lucumaTestAffected` diff against
  `origin/<default branch>`, so the push run narrows the same way its `pull_request` counterpart
  does instead of running everything. A stacked PR, one targeting another branch rather than the
  default, is still measured against the default branch, so its push run sees the parent branch's
  changes too and over-tests.
- **Merge to `main`.** The `affected` job diffs against the previous `main`
  (`github.event.before`), so a merge that can't reach an app doesn't redeploy it. The test run
  does **not**: `lucumaTestAffected` has no base ref there and runs the full suite, which is the
  backstop for everything the dependency graph can't see. A first push or a force-push gives no
  usable commit, and then both fall back to running everything.
- **Tags.** Never narrowed. A tag points into a branch, so diffing against it would find nothing
  changed and publish an untested release.

| Task                   | Description                                                                                 |
| ---------------------- | ------------------------------------------------------------------------------------------- |
| `lucumaAffectedReport` | Log the affected projects, and write `projects` and `all` to `GITHUB_OUTPUT` under Actions. |

Try it locally with `LUCUMA_AFFECTED_BASE=origin/main sbt lucumaAffectedProjects`.

> [!WARNING]
> sbt doesn't know about GraphQL schemas used by codegen, database migrations, or npm
> dependencies. If those live outside the project that uses them, add them to
> `lucumaAffectedAlwaysPaths`.

### `LucumaSlackPlugin`

**Activation:** Automatic (requires `LucumaPlugin`). Disable with
`ThisBuild / lucumaSlackNotify := false`.

Posts to Slack when a workflow fails on the default branch.

GitHub only notifies whoever triggered a run, and merges are pushed by a bot, so a red `main`
reaches nobody. This watches whole workflows rather than instrumenting jobs, so a job added later
is covered without touching anything.

`lucumaSlackNotifyGenerate` writes `.github/workflows/ci-failure-slack.yml` —
`githubWorkflowGenerate` only ever writes `ci.yml` and `clean.yml`, so this has its own task,
checked in CI the way the shared scalafmt and scalafix configs are. `prePR` and Steward regenerate
it. Setting `lucumaSlackNotify := false` and regenerating deletes the file.

You need the webhook as a repository or organization secret. Get one from a Slack app under
_Incoming Webhooks_; it is bound to a single channel. Without it the workflow logs a warning and
exits cleanly, so an unconfigured repo doesn't get a second failure on top of the one it was
reporting.

| Setting                      | Default                         | Description                                                                                                       |
| ---------------------------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `lucumaSlackNotify`          | `true`                          | Set to `false` to not generate the workflow.                                                                      |
| `lucumaSlackNotifyWorkflows` | `Seq("Continuous Integration")` | Workflows to watch. Add a nightly one here: a scheduled run otherwise notifies only whoever last edited its cron. |
| `lucumaSlackNotifyBranch`    | `"main"`                        | Branch whose failures are reported.                                                                               |
| `lucumaSlackWebhookSecret`   | `"GPP_SLACK_WEBHOOK_URL"`       | Name of the Actions secret holding the webhook URL.                                                               |

| Task                        | Description                                               |
| --------------------------- | --------------------------------------------------------- |
| `lucumaSlackNotifyGenerate` | Write the workflow, or delete it when the feature is off. |
| `lucumaSlackNotifyCheck`    | Fail if it is missing, edited by hand, or out of date.    |

The message names the failed jobs, which needs one API call. That call is `continue-on-error`, so a
flaky lookup costs you the job names rather than the whole notification.

### `LucumaRequiredChecksPlugin`

**Activation:** Automatic (requires `LucumaPlugin`, and `LucumaAffectedPlugin` purely so it runs
after it: both append jobs to the workflow, and this one validates against the final list).
Disable with `ThisBuild / lucumaRequiredChecks := false`.

Generates one CI job to require in branch protection, instead of requiring one entry per test shard, JDK and Scala version.

GitHub matches a required check by its literal name, built from the job name plus its matrix values:
`Test (ubuntu-22.04, 3, temurin@25, 0)`. Listing those is fragile twice over. Change a JDK, a Scala
version, a shard count or a job name and the entry silently stops matching. And a job skipped by a job-level `if:` never expands its matrix, so the requirement never matches.

The generated job depends on jobs by **id** through `needs`, which carry none of those values.
`needs: [build]` waits for every combination of `build` and fails if any of them failed:

```scala
ThisBuild / lucumaRequiredCheckJobs := Seq("build", "checks")
```

Each id is checked against the generated workflow, so a typo or a renamed job fails the build
instead of quietly dropping a requirement.

The check to require is the job name plus the one matrix value GitHub appends:

```
REQUIRED CHECKS FOR BRANCH PROTECTION - AGGREGATED (ubuntu-latest)
```

| Setting                   | Default        | Description                                                    |
| ------------------------- | -------------- | -------------------------------------------------------------- |
| `lucumaRequiredChecks`    | `true`         | Set to `false` to not generate the job.                        |
| `lucumaRequiredCheckJobs` | `Seq("build")` | Job ids to require. Each must exist in the generated workflow. |
| `lucumaRequiredChecksJobName` | `"REQUIRED CHECKS FOR BRANCH PROTECTION - AGGREGATED"` | Display name of the job. Keep it the same across repos so there is one string to configure everywhere. |

The job runs `if: always()`, so a skipped dependency can't skip it and leave the check unreported.
It fails on `failure` or `cancelled` and accepts `skipped`: a job the diff made pointless is fine.
It runs on `ubuntu-latest` because sbt-typelevel puts the OS in the check name, and that label never
changes while dated images get retired.

It's generated by default so the name shows up in GitHub's picker from the first push; it does
nothing until branch protection points at it. To adopt: bump, `githubWorkflowGenerate`, push once,
then replace the required-checks list with the single name above.

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

Also brings in [sbt-revolver](https://github.com/indoorvivants/sbt-revolver) for the dev loop,
so `reStart` / `reStop` / `reStatus` / `reStartArgs` are available without each build adding the
plugin itself. This is the `com.indoorvivants` fork: the original `io.spray` plugin has no sbt 2
build. The API is unchanged.

---

## `sbt-lucuma-css`

### `LucumaCssPlugin`

**Activation:** Opt-in — `.enablePlugins(LucumaCssPlugin)` on a Scala.js project.

Collects CSS assets (from both the classpath and dependency jars) into the target directory
as part of the linking step, so stylesheets shipped inside lucuma libraries end up alongside
the linked JS.

| Key                       | Description                                                                 |
| ------------------------- | --------------------------------------------------------------------------- |
| `lucumaCss` (task)        | Copy CSS into `target/lucuma-css`; hooked into `fastLinkJS` / `fullLinkJS`. |
| `lucumaCssExts` (setting) | File extensions treated as CSS (default `css`, `scss`, `saas`).             |

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

| Key                          | Default | Description                                            |
| ---------------------------- | ------- | ------------------------------------------------------ |
| `lucumaDockerDefaultMaxHeap` | `512`   | Max heap (MB) when cgroups don't report a limit.       |
| `lucumaDockerMinHeap`        | `256`   | Minimum heap (MB).                                     |
| `lucumaDockerHeapPercentMax` | `80`    | % of memory for heap when cgroups report `max`.        |
| `lucumaDockerHeapSubtract`   | `0`     | MB to subtract from the memory limit when sizing heap. |
| `lucumaDockerOpenDebugPorts` | `false` | Open JMX / JDWP debug ports in the start script.       |
| `lucumaDockerUseHerokuAgent` | `true`  | Bundle and attach the Heroku Java metrics agent.       |

It also adds sbt 2 lint exclusions for the Debian, Rpm and Linux settings sbt-native-packager
defines but a Docker-only build never reads, which otherwise produce ~90 warnings per project.
The exclusions are scoped to those configurations, so genuinely unused settings in your own
build are still reported.

---

## `lucuma-jsdom` (not published for sbt 2)

`scalajs-env-jsdom-nodejs` has no Scala 3 build, so this module is out of the build until
one exists. Its source is still in `jsdom/`. The last published version is on the 0.16 line,
for sbt 1.

### `LucumaJSDOMNodeJSEnv`

Not an sbt plugin — a custom Scala.js `JSEnv` (extending `JSDOMNodeJSEnv`) added as a build
dependency. It injects browser globals (`document`, `window`, `navigator`, `Event`,
`IS_REACT_ACT_ENVIRONMENT`, …) into the Node realm so React component tests can run under
jsdom. Use it by setting:

```scala
jsEnv := new lucuma.LucumaJSDOMNodeJSEnv()
```
