# Porting a project to sbt-lucuma 0.17 (sbt 2)

sbt-lucuma 0.17 is built for **sbt 2.x** and published as `_sbt2_3`. The 0.16 line is the
last one for sbt 1. There is no cross-build: a project moves to 0.17 and sbt 2 together, in
one commit.

This is the checklist for that commit, in the order the errors actually appear.

## Before you start

- **JDK 17 or later.** sbt 2 requires it. Lucuma builds target 25.
- **A branch you can throw away.** The first `reload` will fail several times in a row; that
  is normal and each failure names its own fix below.
- **`sbt-typelevel` comes from a snapshot.** There is no sbt 2 release upstream yet, so both
  sbt-lucuma and your build resolve it from the gemini-hlsw repo. That resolver is not
  optional, and it is easy to forget in a `project/plugins.sbt` you did not touch.

## 1. Switch the build to sbt 2

`project/build.properties`:

```
sbt.version=2.0.8
```

`project/plugins.sbt`:

```scala
resolvers += "gemini-hlsw" at
  "https://raw.githubusercontent.com/gemini-hlsw/maven-repo/master/releases"

addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % "0.17.0") // or -app, -css, -docker
```

Until 0.17.0 is released, use a local snapshot: run `sbt "++ 3; publishLocal"` in a checkout
of sbt-lucuma and pin the timestamped version it prints.

Then **delete `project/metals.sbt` and `project/project/metals.sbt`**. Metals writes them,
they pull `sbt-jdi-tools`, and that has no `_sbt2_3` build, so sbt refuses to load:

```
[error] not found: .../com/github/sbt/sbt-jdi-tools_sbt2_3/1.2.0/sbt-jdi-tools_sbt2_3-1.2.0.pom
```

Metals regenerates them, so expect to delete them again after opening the editor.

Every other plugin you are likely to depend on already has an `_sbt2_3` build at the version
you are pinning: `sbt-native-packager`, `sbt-header`, `sbt-scalafmt` (2.6.2 or later),
`sbt-scalafix`, `sbt-rewarn`, `sbt-buildinfo`, `sbt-updates`, `sbt-git`, `sbt-scalajs`,
`sbt-scalajs-crossproject`. `sbt-bundlemon` does **not**, which is why sbt-lucuma dropped it.

## 2. Fix `build.sbt` so it loads

### Bare settings now apply to every subproject

This is the change most likely to break a build quietly. In sbt 1 a bare setting belonged to
the root subproject; in sbt 2 it is a *common* setting injected into all of them.

```scala
name := "my-project"   // in sbt 2: every subproject is now called my-project
```

The symptom is an error at load:

```
[error] Overlapping output directories: target/out/jvm/scala-3.x.x/my-project:
[error] 	ProjectRef(..., coreJVM)
[error] 	ProjectRef(..., root)
```

Move `name` into each module. For anything else that genuinely belongs to the root only,
scope it: `LocalRootProject / publish / skip := true`. Bare `enablePlugins(...)` has the same
problem — declare an explicit root project instead:

```scala
lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, app)
```

`ThisBuild / ...` still works and is still the right scope for sbt-typelevel's keys
(`tlBaseVersion`, `crossScalaVersions`, and friends).

### `%%%` becomes `%%`

`%%` is platform-aware in sbt 2, so it encodes the Scala.js suffix on its own:

```scala
libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion
```

If a dependency is published for the JVM only but lives in a cross project, say so
explicitly, or resolution will go looking for a `_sjs1_3` artifact that was never published:

```scala
libraryDependencies += ("org.scala-js" %% "scalajs-test-interface" % scalaJSVersion)
  .platform(Platform.jvm)
```

### Slash syntax only

`test:compile` is gone. Use `Test / compile`.

### `sbt-header` moved package

```diff
- import de.heikoseeberger.sbtheader.HeaderPlugin
+ import sbtheader.HeaderPlugin
```

### `IntegrationTest` is gone

Make it a separate subproject with ordinary tests.

## 3. Fix your custom tasks

### Every task is cached — side effects need `Def.uncached`

This is the subtle one. sbt 2 caches task results to a machine-wide disk cache. On a hit it
returns the cached value *without running the body*, so anything that writes a file, calls
git, or hits the network silently stops happening.

```scala
myCopyTask := Def.uncached {
  IO.copyDirectory(from.value, to.value)
}
```

You will also be forced into it by the compiler whenever a task's result type has no
`JsonFormat`:

```
[error] given evidence sjsonnew.JsonFormat[...] is not found; opt out of caching by
[error] annotating the key with @transient, or as foo := Def.uncached(...)
```

That error is a prompt, not a diagnosis: ask whether the task has side effects before
reaching for the wrapper. If it does, `Def.uncached` is right. If it is genuinely pure and
you want the caching, provide the `JsonFormat`.

### `Classpath` holds `HashedVirtualFileRef`, not `File`

Anything doing I/O over a classpath needs to convert:

```scala
val conv  = fileConverter.value
val files = (Compile / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
```

The reverse direction, for mappings, is `conv.toVirtualFile(file.toPath)`.

### `target.value` moved

It now resolves to `target/out/jvm/scala-<ver>/<project>/`, not `<project>/target/`. Anything
that hardcodes the old path — a vite config, a Dockerfile, a CI artifact glob — needs
repointing. **`lucumaCss` writes there now**, so CSS-consuming frontends must update their
bundler config.

### `test` is an `InputTask`

You can no longer write `Test / test := somethingElse.value`. If you were using that to hook
scripted tests into `test`, make it a separate CI step instead.

### Assorted Scala 3 syntax

The build DSL is Scala 3.8 now, so plugin and `project/*.scala` sources must be too:

| Scala 2.12 | Scala 3 |
| --- | --- |
| `Seq[Setting[_]]` | `Seq[Setting[?]]` |
| `f(xs: _*)` | `f(xs*)` |
| `url("...")` | `uri("...")` |
| `p #> f !` | `(p #> f).!` (no postfix operators) |
| `source.getLines` | `source.getLines()` |

## 4. Fix the command line

sbt 2 takes **one `;`-separated argument**, not several words:

```bash
sbt "clean; compile; testFull"    # good
sbt clean compile test            # [error] Expected whitespace character
```

Two further traps:

- **`test` is incremental now** (it is the old `testQuick`) and its success is cached by
  content hash, surviving `clean`. `testFull` is the old always-run-everything `test`.
- **`sbt '++ 3' foo --bar` is broken** — the aggregated project keys get passed to `foo` as
  arguments. Fold the `++` into the sequence instead: `sbt "++ 3; foo --bar"`.

You do not have to fix the second one in your generated workflow: `LucumaWorkflowSyntaxPlugin`
rewrites every `sbt` line in `.github/workflows/ci.yml` for you. Run
`sbt githubWorkflowGenerate` and commit the result.

## 5. Things that surface only at publish time

Run `sbt "++ 3; publishLocal"` before you call it done. Two failures hide until then:

- **Strict eviction now applies to test dependencies.** Version conflicts sbt 1 swallowed are
  hard errors. Upgrade the dependency; as a stopgap, set a `libraryDependencySchemes` entry.
- **`tlReleaseLocal` does not work** on the sbt 2 sbt-typelevel snapshot
  ([sbt-pgp#246](https://github.com/sbt/sbt-pgp/issues/246)). Use `publishLocal`.

## Known gaps

| What | Status |
| --- | --- |
| `lucuma-jsdom` | Not published for sbt 2. `scalajs-env-jsdom-nodejs` has no Scala 3 artifact. React component tests that use it must stay on the 0.16 line for that dependency. |
| `sbt-bundlemon` | Dropped. No sbt 2 build, so the "Monitor bundle size" CI step is gone. |
| `sbt-jdi-tools` / Metals | No sbt 2 build. Delete the generated `metals.sbt` files whenever they reappear. |
| `sbt-typelevel` | Snapshot only, from the gemini-hlsw repo. Swap the resolver out once upstream releases. |

## References

- [Migrating from sbt 1.x](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html)
- [sbt 2.0 change summary](https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html)
- [sbt-typelevel on sbt 2](https://github.com/zainab-ali/sbt-typelevel/blob/cross-build-all-plugins/SBT-2-migration.md)
