import scala.concurrent.duration._

lazy val a = project.in(file("a"))

// A build that assembles its own job setup from scratch, the way lucuma-odb does to swap the
// checkout step. This replaces the setting outright, so a rewrite attached to
// `githubWorkflowJobSetup` would be lost here.
ThisBuild / githubWorkflowJobSetup := {
  List(WorkflowStep.Checkout) :::
    WorkflowStep.SetupSbt ::
    WorkflowStep.SetupJava(githubWorkflowJavaVersions.value.toList) :::
    githubWorkflowGeneratedCacheSteps.value.toList
}

// An added job built from that custom setup.
ThisBuild / githubWorkflowAddedJobs += WorkflowJob(
  "deploy",
  "Deploy",
  githubWorkflowJobSetup.value.toList :+ WorkflowStep.Run(List("echo deploying"))
)

// Gating on affected projects makes LucumaAffectedPlugin append its own `affected` job, after
// LucumaPlugin's rewrite of the job list has already run.
ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("bundle", "Bundle", List(WorkflowStep.Run(List("echo bundling")))),
  a
)

lazy val checkCacheKey = taskKey[Unit]("Every sbt-caching setup-java step carries the CI-specific cache key")

val DefaultGlobs = List("**/*.sbt", "**/project/build.properties", "**/project/**.scala", "**/project/**.sbt")

def isSbtCachingSetupJava(step: WorkflowStep): Boolean = step match {
  case s: WorkflowStep.Use =>
    s.ref match {
      case UseRef.Public("actions", "setup-java", _) => s.params.get("cache").contains("sbt")
      case _                                         => false
    }
  case _                   => false
}

ThisBuild / checkCacheKey := {
  val all = (ThisBuild / githubWorkflowGeneratedCI).value

  // the jobs this must protect: the generated build, a consumer's added job, and a job another
  // lucuma plugin appends after the rewrite
  List("build", "deploy", "affected").foreach { id =>
    val job   = all.find(_.id == id).getOrElse(sys.error(s"no `$id` job: ${all.map(_.id)}"))
    val steps = job.steps.filter(isSbtCachingSetupJava)
    if (steps.isEmpty) sys.error(s"`$id` has no sbt-caching setup-java step")
    steps.foreach {
      case s: WorkflowStep.Use =>
        val path  = s.params.getOrElse("cache-dependency-path", sys.error(s"`$id`: setup-java has no cache-dependency-path"))
        val globs = path.split("\n").toList
        // setup-java's defaults must survive: passing the input replaces them
        DefaultGlobs.filterNot(globs.contains).foreach(g => sys.error(s"`$id`: cache key lost default glob $g"))
        if (!globs.contains(".github/workflows/ci.yml"))
          sys.error(s"`$id`: cache key does not include ci.yml: $globs")
      case _                   => ()
    }
  }

  // the update step is retried as a whole and also fetches the compiler bridge
  List("build", "deploy", "affected").foreach { id =>
    val job    = all.find(_.id == id).get
    val update = job.steps.find(_.name.contains("sbt update")).getOrElse(sys.error(s"`$id`: no update step"))
    update match {
      case r: WorkflowStep.Run =>
        val script = r.commands.mkString("\n")
        if (!script.contains("for attempt in 1 2 3")) sys.error(s"`$id`: update is not retried:\n$script")
        if (!script.contains("sbt +update +scalaCompilerBridgeBinaryJar"))
          sys.error(s"`$id`: update does not fetch the compiler bridge:\n$script")
        if (!r.cond.exists(_.contains("cache-hit == 'false'"))) sys.error(s"`$id`: lost the cache-miss condition: ${r.cond}")
      case other               => sys.error(s"`$id`: update step is still ${other.getClass.getSimpleName}")
    }
  }

  // no sbt-caching setup-java step anywhere is left on the default key
  all.foreach { job =>
    job.steps.filter(isSbtCachingSetupJava).collect { case s: WorkflowStep.Use => s }.foreach { s =>
      if (!s.params.contains("cache-dependency-path"))
        sys.error(s"`${job.id}`: setup-java on the shared default cache key")
    }
  }
}

lazy val checkRetries = taskKey[Unit]("Coursier retries are raised in CI and in resolution")

ThisBuild / checkRetries := {
  val env = (ThisBuild / githubWorkflowEnv).value
  val opts = env.getOrElse("SBT_OPTS", sys.error(s"no SBT_OPTS in workflow env: $env"))
  if (!opts.contains("-Dlmcoursier.internal.shaded.coursier.exception-retry=10"))
    sys.error(s"SBT_OPTS does not raise download retries: $opts")

  val retry = (a / csrConfiguration).value.retry
  if (retry != Some((5.seconds, 10))) sys.error(s"unexpected resolution retry: $retry")
}
