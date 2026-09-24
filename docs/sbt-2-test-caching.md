# Can sbt 2 caching avoid re-running tests?

Researched against sbt 2.0.8 sources (tag [`v2.0.8`](https://github.com/sbt/sbt/tree/v2.0.8),
commit `3127e8d`) and the official sbt 2.x documentation. Every claim below links to the doc
page or source line that owns it. Blog posts are cited only where eed3si9n (the sbt lead) is
the first-party author.

## Answer

Yes. In sbt 2 the `test` task is an *incremental* test task: each test suite that passes is
recorded as a successful action in the same action cache that backs everything else, and a
later `test` skips any suite whose digest is already marked passed
([`IncrementalTest.scala#L36`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L36),
[`#L126`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L126)).
That state lives in the machine-wide disk cache (`~/Library/Caches/sbt/v2/{ac,cas}` on macOS,
`~/.cache/sbt/v2` on Linux), survives `clean`, and is destroyed by `cleanFull`. The markers are
tagged `CacheLevelTag.all` and **do** round-trip through the remote cache: verified 2026-09-22
against BuildBuddy from an empty local cache (`phase0/test` in lucuma-odb ran 0 of 12 suites,
39 remote hits). The catch is that the gRPC store only exists when `addRemoteCachePlugin` is in
`project/plugins.sbt`; `Global / remoteCache` alone is accepted and silently ignored, which is
why our earlier identical-commit CI runs both ran the full suite.

---

## 1. Is `test` incremental, and is the result cached by content hash?

Yes, and it is a different mechanism from the general task cache.

The sbt 2 changes page states plainly: *"test task is changed to be incremental test that can
cache test results. Use testFull for full test"*, and *"test is incremental and cached. This
means, the test will not run unless it previously failed or something changed since the last
run."*
([sbt 2.0 changes](https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html)).

The `sbt test` reference lists the conditions under which a suite actually runs:

> In addition to the explicit filter, the `test` task runs only the tests that satisfy one of
> the following conditions are run:
> - The tests that failed in the previous run
> - The tests that were not run before
> - The tests that have one or more transitive dependencies, maybe in a different project,
>   recompiled.

and describes the default `test` as **Cached**: *"The test result is cached machine-wide, and
optionally remote cached."*
([sbt test](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html)).

**The mechanism is a filter, not a cached task result.** In `Defaults.scala`:

- [`test := testQuick.evaluated`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1270)
  — `test` *is* `testQuick`.
- [`testQuick / testFilter := Def.uncached(IncrementalTest.filterTask.value)`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1226)
  — the incrementality is a per-class name filter.
- [`executeTests := Def.uncached { ... }`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1228)
  and [`testFull := Def.uncached { ... }`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1249)
  — the task that actually runs tests is explicitly **not** a cached task.

So there is no `JsonFormat[Tests.Output]` round-trip and no "restore the test task's result".
What is cached is one zero-byte-ish *success marker per test suite*, keyed by a content digest.

`IncrementalTest.filterTask` builds the filter by asking the action cache whether a marker
exists ([lines 29–50](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L29-L50)):

```scala
def hasCachedSuccess(ts: Digest, options: Seq[String]): Boolean =
  val input = cacheInput(ts, options)
  ActionCache.exists(input._1, input._2, input._3, config)
...
yield (test: String) => filter(test) && !hasSucceeded(test, options)
```

The marker is written by `TestStatusReporter`, a `TestsListener` injected into `testListeners`
([`Defaults.scala#L1302`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1302)),
whose `endGroup` stores an action result with exit code 0 when the suite passes
([`IncrementalTest.scala#L118-L136`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L118-L136)):

```scala
ActionCache.cache(
  key = input._1,
  codeContentHash = input._2,
  extraHash = input._3,
  tags = CacheLevelTag.all.toList,
  config = cacheConfiguration,
): (_) => ActionCache.actionResult(successfulTest)
```

**Does it survive `clean`?** Yes. `clean` deletes the project's output directory only. The
machine-wide store is cleared by `cleanFull`, which explicitly calls `clear()` on the disk store
before running `clean` and deleting the root output directory
([`Clean.scala#L217-L232`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/Clean.scala#L217-L232)):

```scala
cacheStore.foreach:
  case d: DiskActionCacheStore => d.clear()
  case _                       => ()
```

This is corroborated by sbt's own scripted test for incremental testing, which does `> compile`
between edits and still expects suites to be skipped
([`sbt-app/src/sbt-test/tests/incremental/test`](https://github.com/sbt/sbt/blob/v2.0.8/sbt-app/src/sbt-test/tests/incremental/test)).

## 2. Where does the test-result state live?

In the **action cache (`ac/`)** of whatever stores are configured — by default the local disk
store, plus the gRPC remote store when `Global / remoteCache` is set. There is no separate
"succeeded tests" file the way sbt 1 had one.

The default local cache directory is `SysProp.globalLocalCache`
([`RemoteCache.scala#L28-L31`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/RemoteCache.scala#L28-L31)),
resolved in this precedence order
([`SysProp.scala#L230-L260`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/SysProp.scala#L230-L260)):

| Source | Value |
| --- | --- |
| `-Dsbt.global.localcache=<dir>` | that directory |
| `-Dsbt.global.base=<dir>` | `<dir>/cache` |
| `SBT_LOCAL_CACHE` env var | that directory |
| Windows | `%LOCALAPPDATA%/sbt` |
| macOS | `$HOME/Library/Caches/sbt` |
| Linux / other | `$XDG_CACHE_HOME/sbt`, else `$HOME/.cache/sbt` |

…and then `/v2` is appended unconditionally (`baseCache.getAbsoluteFile / "v2"`). So:

- **macOS:** `~/Library/Caches/sbt/v2/`
- **Linux:** `~/.cache/sbt/v2/` (or `$XDG_CACHE_HOME/sbt/v2/`)

Inside that directory `DiskActionCacheStore` creates two subdirectories, `cas/` for
content-addressed blobs and `ac/` for action results
([`ActionCacheStore.scala#L185-L198`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/ActionCacheStore.scala#L185-L198)).
This matches the layout eed3si9n described when introducing the feature
([sbt 2.x remote cache](https://eed3si9n.com/sbt-remote-cache/)) and matches the
`~/Library/Caches/sbt/v2/cas/` directory we observe locally.

Test success markers go into `ac/` (each a tiny JSON `ActionResult` with `exitCode = 0`), which
is why they do not show up as a large `cas/` contribution.

## 3. Is `test` remote-cacheable at all?

**The `test` task itself: no.** `executeTests` and `testFull` are wrapped in `Def.uncached`
([L1228](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1228),
[L1249](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1249)),
so there is no `JsonFormat` round-trip of `Tests.Output` and no action-cache entry for the task.
`Def.uncached` is the documented opt-out from task caching
([Caching](https://www.scala-sbt.org/2.x/docs/en/concepts/caching.html)).

**The per-suite success markers: yes, by construction.** They are written with
`tags = CacheLevelTag.all.toList`, i.e. `Array(CacheLevelTag.Local, CacheLevelTag.Remote)`
([`ActionCache.scala#L523`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/ActionCache.scala#L523)),
and both the write and the read go through `config.store`, which is an
`AggregateActionCacheStore` over every configured store
([`Def.scala#L288-L310`](https://github.com/sbt/sbt/blob/v2.0.8/main-settings/src/main/scala/sbt/Def.scala#L288-L310)).
`AggregateActionCacheStore.put` writes to **all** stores; `get` returns the first hit
([`ActionCacheStore.scala#L91-L115`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/ActionCacheStore.scala#L91-L115)).

`RemoteCachePlugin` appends a `GrpcActionCacheStore` to `cacheStores` whenever
`Global / remoteCache` is set
([`RemoteCachePlugin.scala#L12-L31`](https://github.com/sbt/sbt/blob/v2.0.8/sbt-remote-cache/src/main/scala/sbt/plugins/RemoteCachePlugin.scala#L12-L31)),
so the aggregate becomes `[disk, grpc]`. **That plugin is a separate artifact**
(`org.scala-sbt:sbt-remote-cache`), not part of `org.scala-sbt:sbt`; it is added with
`addRemoteCachePlugin` in `project/plugins.sbt`
([`Defaults.scala`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala),
[remote-cache/basic/project/plugins.sbt](https://github.com/sbt/sbt/blob/v2.0.8/sbt-app/src/sbt-test/remote-cache/basic/project/plugins.sbt)).
The `remoteCache` key itself lives in core sbt, so `show Global/remoteCache` prints `Some(...)`
even when no store consumes it. Worse, `GrpcActionCacheStore` returns every failure as `Left(e)`
and the aggregate treats `Left` as a miss, so a misconfigured remote is indistinguishable from an
empty one without inspecting `Def.cacheConfiguration.value.store`. `ActionCache.exists` therefore checks disk first and
falls through to the remote server's `GetActionResult`
([`ActionCache.scala#L260-L282`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/ActionCache.scala#L260-L282),
[`GrpcActionCacheStore.scala#L231-L242`](https://github.com/sbt/sbt/blob/v2.0.8/sbt-remote-cache/src/main/scala/sbt/internal/GrpcActionCacheStore.scala#L231-L242)).

**But sbt does not test this path.** The only remote-cache scripted test is
[`sbt-app/src/sbt-test/remote-cache/basic`](https://github.com/sbt/sbt/blob/v2.0.8/sbt-app/src/sbt-test/remote-cache/basic/test),
which exercises `compile` only. Conversely the incremental-test scripted case pins
`Global / cacheStores := Seq.empty`
([`build.sbt`](https://github.com/sbt/sbt/blob/v2.0.8/sbt-app/src/sbt-test/tests/incremental/build.sbt)),
so it runs against a throwaway `target/bootcache` disk store and never touches gRPC. There is no
upstream coverage of "suite passed on machine A, skipped on machine B". We verified it ourselves
(see Answer); the identical-commit CI runs that executed ~4700 tests each did so because the
plugin was missing, not because the path is broken.

## 4. Restoring the state on a fresh CI runner

There is **no sbt-documented, endorsed recipe** for this. The remote-cache setup page covers
only `remoteCache`, TLS and headers — it says nothing about CI, about `localCacheDirectory`, or
about persisting it ([Remote cache setup](https://www.scala-sbt.org/2.x/docs/en/reference/remote-cache-setup.html)).

What the source supports, if you want to try it: the whole of the local store is one directory,
and it is relocatable by env var. Caching it with `actions/cache` would mean caching

- `~/.cache/sbt/v2/` on Linux runners, or
- a path you pin yourself via `SBT_LOCAL_CACHE=<dir>` / `-Dsbt.global.localcache=<dir>`
  ([`SysProp.scala#L232-L238`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/SysProp.scala#L232-L238)).

Two caveats before doing that. First, the directory holds `cas/` as well as `ac/` and grows
without bound (4.6 GB locally here) — there is no eviction in `DiskActionCacheStore`, only the
all-or-nothing `clear()`. Second, restoring a stale `ac/` is exactly the correctness hazard in
§5: an entry whose digest inputs are incomplete will silently skip a suite.

## 5. What feeds the digest?

The key for a suite is `cacheInput(ts, frameworkOptions)` =
`(frameworkOptions, ts, Digest.zero)`
([`IncrementalTest.scala#L94-L99`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L94-L99)),
where `ts` comes from `definedTestDigestTask`
([L53-L69](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L53-L69)):

```scala
val stamper = ClassStamper(cp, converter)
val testDigestExtra = extra ++ rds ++ opts
... stamper.transitiveStamp(name, testDigestExtra, s.log)
```

**In the hash:**

| Input | Where |
| --- | --- |
| Transitive class bytecode hashes for the suite, walked through the zinc `Analysis` on the full classpath | [`ClassStamper.transitiveStamp`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L207-L220) |
| Content digests of every library jar reached transitively | [`CacheImplicits.virtualFileRefToDigest`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/CacheImplicits.scala#L125-L141) — content-hash based, so machine-independent |
| `resourceDigests` — managed + unmanaged resource file stamps | [`Defaults.scala#L688-L695`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L688-L695) |
| `testOptionDigests` — the `Tests.Setup`/`Tests.Cleanup` **caller-supplied** `codeDigest`, and `Tests.Argument(framework, args)` | [`Defaults.scala#L1312-L1324`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L1312-L1324) |
| `extraTestDigests`, which by default is `extraIncOptions` | [`IncrementalTest.scala#L71-L79`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/IncrementalTest.scala#L71-L79) |
| The test framework arguments passed on the command line | `cacheInput`'s first element |
| `Global / cacheVersion` (global invalidation knob, `-Dsbt.cacheversion`) | [`ActionCache.scala#L81`](https://github.com/sbt/sbt/blob/v2.0.8/util-cache/src/main/scala/sbt/util/ActionCache.scala#L81), [`RemoteCache.scala#L33`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/RemoteCache.scala#L33) |

**The JDK is only weakly represented.** `extraIncOptions` defaults to a single pair:

```scala
extraIncOptions :== Seq("JAVA_CLASS_VERSION" -> sys.props("java.class.version"))
```
([`Defaults.scala#L184`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/Defaults.scala#L184))

So the hash sees the JDK *major class-file version* (`69.0` for JDK 25) and nothing else — not
the vendor, not the patch level, not the GC or any other JVM flag. Two runs on Temurin 25.0.1
and GraalVM 25.0.4 produce the same digest. The comment in `extraTestDigestsTask` calls this
"by default this captures JVM version", which overstates what `java.class.version` actually is.

**NOT in the hash** (verified by grepping the digest inputs above):

- `Test / envVars` and the ambient process environment
- `Test / javaOptions`, fork options, `Test / fork`, the working directory
- Arbitrary `-D` system properties (only `sbt.cacheversion` participates, and only as
  `cacheVersion`)
- Anything a test reads at runtime that is not a classpath resource — files outside
  `resources`, databases, network services, the clock
- The *body* of a `Tests.Setup`/`Tests.Cleanup` block. `Tests.Setup` is
  `case class Setup(setup: ClassLoader => Unit, codeDigest: Digest)`
  ([`Tests.scala#L82`](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/Tests.scala#L82));
  the digest is supplied by whoever constructs it. A plugin that passes a constant digest makes
  its setup invisible to invalidation.

Practical consequence for a Lucuma-shaped build: a suite that talks to a containerised
PostgreSQL, reads `Test / envVars`, or depends on `javaOptions` can be marked passed and then
skipped after the environment changes. That is the silent-stop-running failure mode.

## 6. Documented caveats from sbt

sbt's own documentation is thin here. What exists:

- `testFull` is the documented escape hatch: *"To run, uncached full tests, like sbt 1.x, use
  the `testFull` task."*
  ([sbt test](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html)).
- Forked tests do not get `Setup`/`Cleanup` with a real class loader — *"Setup and Cleanup
  actions cannot be provided with the actual test class loader when a group is forked"* (same
  page; also [`Tests.scala#L80`](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/Tests.scala#L80),
  *"Setup is not currently performed for forked tests"*).
- `Def.uncached` is the documented per-task opt-out from caching
  ([Caching](https://www.scala-sbt.org/2.x/docs/en/concepts/caching.html)); wrapping a test task
  in it, or using `testFull`, is how you force execution.
- `cacheVersion` / `-Dsbt.cacheversion` exists precisely as a global invalidation lever
  ([`RemoteCache.scala#L33`](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/RemoteCache.scala#L33)),
  and there is a scripted test for it
  ([`sbt-app/src/sbt-test/actions/cache-version`](https://github.com/sbt/sbt/tree/v2.0.8/sbt-app/src/sbt-test/actions/cache-version)).

I found **no** sbt-authored warning about environment-dependent tests being wrongly skipped.
That gap is ours to mind.

---

## Decision for our builds (2026-09-22)

- `lucumaTestAffected` runs the task named by `ThisBuild / lucumaAffectedTestTask`, default
  `testFull`: the documented, uncached equivalent of sbt 1's `test`. Affected-project selection
  still limits CI to what a change can break.
- A build that wants per-suite skipping too sets it to `test`. lucuma-odb does, with BuildBuddy
  as the shared store. It must bump `sbt.cacheversion` whenever the JDK, container images, or
  `Test / envVars` change, because none of those are in the digest.
- Any build using `Global / remoteCache` also needs `addRemoteCachePlugin` in
  `project/plugins.sbt`, or the setting does nothing.

## Unknowns / could not verify

- ~~Why our remote cache does not serve test markers.~~ Resolved: `addRemoteCachePlugin` was
  missing, so no gRPC store was ever configured.
- ~~Why a *local* run with the remote cache configured still ran everything.~~ Same cause.
- **Whether `tags` actually gates which stores are consulted.** `ActionCache.cache` and
  `getWithFailure` take a `tags: List[CacheLevelTag]` but I could not find where the tag list
  filters `config.store`; the aggregate store appears to be used unconditionally. I did not
  trace the `@cacheLevel` macro path far enough to be sure.
- **Whether `clean` leaves the markers intact in practice.** I verified `cleanFull` clears the
  disk store and that `clean` has no such call, but I did not run an experiment.
- **Why test-classes recompile on a remote-only hit.** With main classes served from BuildBuddy,
  the single test source still recompiled (10 misses) yet the suites were skipped. The test
  compile digest differs between machines in some way the suite digest does not. Unexplained.
- **`~/.cache/sbt/v2` on Linux is derived from source, not from a doc.** sbt's docs state the
  *global base* directory convention (`$XDG_CONFIG_HOME/sbt/2`) but I found no doc page stating
  the *cache* directory. The path above comes from `SysProp.globalLocalCache` and from
  eed3si9n's post showing the macOS equivalent.
- **No sbt release note explicitly describes remote-cached test skipping.** The 2.0 change
  summary says "machine-wide, and optionally remote cached" (echoing the reference page); I
  found no changelog entry, PR, or scripted test demonstrating it working across machines.
