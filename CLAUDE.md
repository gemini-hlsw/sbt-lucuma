# CLAUDE.md

Guidance for working in this repo. For end-user docs of each plugin, see `README.md`.

## What this is

`sbt-lucuma` is a set of **sbt AutoPlugins** providing shared build settings for Gemini
lucuma projects (Scala version, CI/release, publishing, headers, scalafmt/scalafix, Scala.js,
CSS bundling, Docker packaging). It is built on [sbt-typelevel](https://github.com/typelevel/sbt-typelevel).

## Key facts (read before editing)

- **Scala 2.12.21.** These are sbt 1.x plugins, which run on 2.12 — *not* Scala 3. Don't use
  Scala 3 syntax here.
- **Self-bootstrapping / dogfooding.** `project/plugins.sbt` adds the `core` and `lib` plugin
  sources directly as `unmanagedSourceDirectories` of the meta-build, so this build uses its
  own plugins to build itself (e.g. `lucumaScalafmtCheck`/`lucumaScalafixCheck` run in CI
  here). Consequence: editing a `core`/`lib` plugin can change how *this* repo's own build
  behaves, and a compile error there breaks the meta-build. Run a full `reload` after such
  edits.
- **Duplicated version pins.** `sbtTypelevelVersion` and `scalaJsVersion` are declared in
  **both** `build.sbt` and `project/plugins.sbt` (with "update in the other as well" comments).
  Bump both.

## Module layout

Each module is a separate sbt subproject and a separately published artifact. `app`, `lib`,
and `docker` depend on `core`.

| Dir | Artifact | Notable contents |
| --- | --- | --- |
| `core/` | `sbt-lucuma` | `LucumaPlugin` (umbrella), `LucumaScalaJSPlugin`, `LucumaScalafmtPlugin`, `LucumaScalafixPlugin`, `LucumaBundleMonPlugin`. Bundled resources: `scalafmt-common.conf`, `scalafix-common.conf`. |
| `lib/` | `sbt-lucuma-lib` | `LucumaLibPlugin` (published libraries; adds MiMa). |
| `app/` | `sbt-lucuma-app` | `LucumaAppPlugin` (applications; date+git version, no MiMa). |
| `css/` | `sbt-lucuma-css` | `LucumaCssPlugin` (opt-in CSS bundling). Has scripted tests. |
| `docker/` | `sbt-lucuma-docker` | `LucumaDockerPlugin`. `BuildInfoPlugin` exposes `HerokuAgentVersion`; bundled `docker-set-memory.sh`. |
| `jsdom/` | `lucuma-jsdom` | `LucumaJSDOMNodeJSEnv` — a Scala.js `JSEnv`, **not** a plugin; added as a `libraryDependencies` in consumers' `project/`. |

## Plugin conventions

- Package `lucuma.sbtplugin`; objects extend `sbt.AutoPlugin`.
- Public keys go in a nested `object autoImport { ... }`.
- **Activation** is governed by `requires` + `trigger`:
  - `trigger = allRequirements` → auto-enables wherever its `requires` are satisfied. A plugin
    requiring `ScalaJSPlugin` therefore activates *only on Scala.js projects* (the JS side of a
    crossProject) — this is how `LucumaScalaJSPlugin` keeps its settings JS-only.
  - No `trigger` override (default `noTrigger`) → opt-in; consumers must
    `.enablePlugins(...)` (e.g. `LucumaCssPlugin`).
- Settings live in `globalSettings` / `buildSettings` / `projectSettings` as appropriate.
- All `.scala` files carry the BSD-3-Clause AURA header (use `headerCreateAll` to apply).

## Common commands

```bash
sbt compile                 # compile all modules (note: meta-build also compiles core/lib)
sbt test                    # runs module tests; for css this runs the scripted tests
sbt css/scripted            # run css scripted tests directly
sbt <module>/publishLocal   # publish one artifact to ~/.ivy2/local for manual testing
sbt headerCreateAll         # apply license headers
sbt scalafmtAll scalafmtSbt # format
```

CI runs (see `.github/workflows/ci.yml`):

```bash
sbt headerCheckAll scalafmtCheckAll 'project /' scalafmtSbtCheck lucumaScalafmtCheck lucumaScalafixCheck
sbt test
sbt mimaReportBinaryIssues
sbt tlCiRelease            # publish (release job)
```

## Testing a plugin change end-to-end

`scripted` (in `css/src/sbt-test/...`) is the in-repo way to test plugin behavior. To verify a
change against a real consuming build:

1. `sbt <module>/publishLocal` and note the printed `-SNAPSHOT` version.
2. In a throwaway build, `addSbtPlugin("edu.gemini" % "<artifact>" % "<snapshot>")` (or
   `libraryDependencies += ... % lucuma-jsdom` in `project/`), then inspect the resulting
   settings, e.g. `show fooJS/Test/testOptions` vs `show fooJVM/Test/testOptions`.

## Releasing

Released via sbt-typelevel CI (`tlCiRelease`) on the `main` branch / version tags. Versions are
derived by `tlBaseVersion` (currently `0.14`) — do not hand-edit version numbers.
