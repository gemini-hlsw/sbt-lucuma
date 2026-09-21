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

`addDependencyTreePlugin` is one you can simply delete. sbt 2 ships `dependencyTree` and
`whatDependsOn` in core, so the common cases, reading the graph and hunting an eviction, need no
plugin. The extras do not survive: `dependencyList`, `dependencyDot`, `dependencyStats`,
`dependencyBrowseGraph` and `dependencyBrowseTree` are all gone, with no replacement for the
browser views.

`sbt-revolver` is the awkward one. The original `io.spray` artifact has no sbt 2 build, but
[indoorvivants/sbt-revolver](https://github.com/indoorvivants/sbt-revolver) does and keeps the
same API, so `reStart` / `reStop` / `reStartArgs` survive the switch. sbt-lucuma brings it in
from core, so a lucuma build needs nothing. Anything else adds it directly:

```scala
addSbtPlugin("com.indoorvivants" % "sbt-revolver" % "0.11.2")
```

Do not reach for `bgRun` as a substitute without reading the next section first.

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

Redefining `compile` (to sequence `copyResources` ahead of a macro, say) hits this, and there
the wrapper is free: sbt defines `compile := Def.uncached(compileTask.value)` itself, along with
`compileIncremental`, `compileEarly`, `compileJava` and `compileScalaBackend`. `compile` is not
task-cached for anyone; zinc's analysis store does the caching, and redefining the task does not
disturb it.

### `Classpath` holds `HashedVirtualFileRef`, not `File`

Anything doing I/O over a classpath needs to convert:

```scala
val conv  = fileConverter.value
val files = (Compile / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
```

The reverse direction, for mappings, is `conv.toVirtualFile(file.toPath)`.

### `target.value` moved

It now resolves to `target/out/jvm/scala-<ver>/<artifact>/`, not `<project>/target/`. Anything
that hardcodes the old path — a vite config, a Dockerfile, a CI artifact glob — needs
repointing. **`lucumaCss` writes there now**, so CSS-consuming frontends must update their
bundler config.

The last path segment is the **artifact name**, not the module directory, and the Scala version
is baked in:

```diff
- modules/core/jvm/target
+ target/out/jvm/scala-3.9.0/lucuma-core
```

sbt-typelevel's "Make/Compress target directories" CI steps are full of these. They are
regenerated by `githubWorkflowGenerate`, so do not hand-edit them, but expect a large and
alarming diff.

### `bgRun` is not a drop-in for `reStart`

If you replace revolver with sbt's own background jobs, two differences bite, both silently:

- **`bgRun` does not fork.** An unforked run ignores `envVars` and `javaOptions` entirely, so a
  service configured through `run / envVars` starts with none of its configuration. Set
  `run / fork := true`.
- **There is no `reStartArgs`.** `bgRun` takes arguments only at the call site, so a service
  whose subcommand used to be supplied by `reStartArgs += "serve"` now starts with no
  subcommand and prints its usage banner. An `inputKey` wrapping `Compile / bgRun` restores it:

```scala
lazy val devRun = inputKey[JobHandle]("Run this service in the background")

def devRunSetting(defaults: String*) =
  devRun := Def.inputTaskDyn {
    val extra = Def.spaceDelimited("<arg>").parsed
    (Compile / bgRun).toTask((defaults.toList ::: extra.toList).map(" " + _).mkString)
  }.evaluated
```

`bgRun` also lacks `reStart`'s kill-and-replace: calling it twice gives you two running jobs.

### JVM options only apply when the server starts

Same root cause, different symptom. `sbt -J-Xmx6g ...` on a *later* invocation is ignored: the
thin client hands the command to a server that is already running, and that server booted with
whatever options were in force at the time — the default 1GB heap if nothing set them.

On CI this shows up as an `OutOfMemoryError` in a step whose command clearly asks for more:

```
[warn] ... [Heap: 0.00GB free of 1.00GB, max 1.00GB]
java.lang.OutOfMemoryError: Java heap space
```

sbt-typelevel's `githubWorkflowSbtCommand := "sbt -J-Xmx6g"` is an sbt 1 idiom for this reason.
Set the options for the whole workflow instead, so every step agrees:

```scala
ThisBuild / githubWorkflowEnv += ("SBT_OPTS" -> "-Xmx6g -Xss4M")
ThisBuild / githubWorkflowSbtCommand := "sbt -v"
```

Locally the same job is done by `.jvmopts`, which is often gitignored — which is exactly why the
failure appears on CI only.

### A forked process inherits the sbt *server's* environment

This one is worth knowing before it costs you an afternoon. sbt 2 keeps a background server, and
a later `sbt` invocation attaches to whichever server is already running for that build. Anything
it forks inherits **that server's** environment, not the shell you typed in.

So a service can keep starting with stale configuration long after you fixed your shell — the
variable is right in front of you and wrong in the running process. `project/target/active.json`
names the socket of the server in use. Kill it, or `shutdown`, after changing anything the
application reads from the environment.

### An environment lookup in `build.sbt` may read nothing at all

Worse than the stale-environment case above, and the one that actually costs money. The build
DSL runs **inside the server**, so `sys.env` there is not reading the environment of the `sbt`
command you just typed. In lucuma-odb's CI it read nothing: `System.getenv` came back `null` for
a variable the workflow step demonstrably set.

The failure is silent and green. Anything gated this way simply takes its default:

- `sbt-test-shards` never saw `TEST_SHARD`, so all **eight** shards fell back to "shard 0 of 1"
  and each ran the entire suite. Eight runners, identical work, no parallelism, every job
  passing.
- A suite skipped unless `RUN_LEGACY_TESTS=true` was never enabled, so the nightly job ran zero
  tests and reported success.

Pass anything the build must read as a **system property** through `SBT_OPTS`, which the
launcher applies to the server JVM as it starts — the same route the heap settings already take:

```yaml
SBT_OPTS: '-Xmx6g -Dtest.shard=${{ matrix.shard }} -Dtest.shard.count=8'
```

and read it with `sys.props.get`.

**Set it on the job, never on one step.** The properties belong to the server, and the server
takes them from whichever `sbt` invocation happens to start it — which is rarely the step you
care about. lucuma-odb put the shard on its test step and it changed nothing: `sbt update` runs
six steps earlier, so by the time the tests ran they were talking to a server that had never
seen the flag. Every shard reported `#0` and ran all 472 suites, while its own step's
`SBT_OPTS` sat there visibly correct in the log.

Where the value comes from the matrix, workflow-level `env` cannot see it, so put it on the job:

```scala
ThisBuild / githubWorkflowGeneratedCI ~= {
  _.map { job =>
    if (job.id == "build")
      job.withEnv(job.env + ("SBT_OPTS" -> s"-Xmx6g -Dtest.shard=$${{ matrix.shard }}"))
    else job
  }
}
```

Repeat the heap flags there, because a job-level value replaces the workflow-level one.

Test code is a separate problem. It runs in a forked JVM, which inherits the *server's*
environment and does not inherit the server's system properties, so neither half of this reaches
it. Use `Test / envVars` or `Test / javaOptions` for that.

**How to tell whether a gate is live.** Never by the job's status; it stays green either way.
Read the test counts (`Passed: Total N`) or log the value you branched on.

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

### Resources reach the classpath inside a jar

sbt 2 packages a module's resources before putting them on the classpath, so a resource URL is
no longer a filesystem path. Test code that did this stops working:

```scala
val path = getClass.getResource("/fixture.xml").getPath   // a jar entry, not a file
Files[IO].readAll(Path(path))                             // NoSuchFileException
```

Go through the classloader instead, which works under both sbt 1 and sbt 2:

```scala
fs2.io.readClassLoaderResource[IO]("fixture.xml")         // no leading slash
getClass.getResourceAsStream("/fixture.xml").readAllBytes()
```

If several suites need it, put a small helper in a testkit module rather than repeating the
conversion. This bites fixture-reading tests specifically, and only at run time.

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
  arguments. Fold the `++` into the sequence instead: `sbt "++ 3; foo --bar"`. This is what
  made `scalafixAll --check` look broken; the key itself is fine.

You do not have to fix the second one in your generated workflow: `LucumaWorkflowSyntaxPlugin`
rewrites every `sbt` line in `.github/workflows/ci.yml` for you. Run
`sbt githubWorkflowGenerate` and commit the result.

### The generated target directories come out in a random order

`githubWorkflowCheck` then fails on CI against a file you generated locally, with a diff whose
two sides hold the same paths in a different order. sbt-typelevel accumulates them through
`Global / internalTargetAggregation ++= Seq(target.value)` per project, so the order follows
however sbt applied the project settings — not stable across machines.

It is much more visible on sbt 2, because the unified `target/out/...` layout puts every path in
these two lines. `LucumaPlugin` sorts them, so a lucuma build needs nothing. Anything else does
it in the build, and both sides then agree:

```scala
ThisBuild / githubWorkflowGeneratedUploadSteps ~= { steps =>
  val prefixes = List("mkdir -p ", "tar cf targets.tar ")
  steps.map {
    case run: WorkflowStep.Run =>
      run.withCommands(run.commands.map { cmd =>
        prefixes.find(cmd.startsWith) match {
          case Some(prefix) =>
            prefix + cmd.drop(prefix.length).split(' ').sorted.mkString(" ")
          case None => cmd
        }
      })
    case other => other
  }
}
```

`internalTargetAggregation` itself is private, but `githubWorkflowGeneratedUploadSteps` is a
public setting, and the rewrite runs wherever generation runs — CI's check included.

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
| `sbt-revolver` | The `io.spray` artifact has no sbt 2 build. Use the `com.indoorvivants` fork, which keeps the same API. |
| `sbt-typelevel` | Snapshot only, from the gemini-hlsw repo. Swap the resolver out once upstream releases. |

## References

- [Migrating from sbt 1.x](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html)
- [sbt 2.0 change summary](https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html)
- [sbt-typelevel on sbt 2](https://github.com/zainab-ali/sbt-typelevel/blob/cross-build-all-plugins/SBT-2-migration.md)
