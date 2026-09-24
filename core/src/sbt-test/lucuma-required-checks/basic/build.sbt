lazy val a = project.in(file("a"))

// a conditionally skipped job: the gate must depend on it by id and tolerate it being skipped
ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("deploy", "Deploy", List(WorkflowStep.Run(List("echo deploying")))),
  a
)

ThisBuild / lucumaRequiredCheckJobs += "deploy"
ThisBuild / lucumaRequiredChecksJobName := "Custom name"

lazy val checkGate    = taskKey[Unit]("The gate depends on jobs by id and always runs")
lazy val checkOff     = taskKey[Unit]("No gate when disabled")

ThisBuild / checkGate := Def.uncached {
  val job = (ThisBuild / githubWorkflowGeneratedCI).value
    .find(_.id == "required-checks")
    .getOrElse(sys.error("no `required-checks` job"))

  // by id, so no matrix values, shard numbers or JDK versions leak into the required name
  if (job.needs.sorted != List("build", "deploy")) sys.error(s"needs ${job.needs}")

  // must run even when a dependency is skipped, or the required check never reports
  if (job.cond != Some("always()")) sys.error(s"cond ${job.cond}")

  // one axis, and a label that never changes, so the check name is stable
  if (job.oses != List("ubuntu-latest")) sys.error(s"oses ${job.oses}")
  if (job.scalas.nonEmpty || job.javas.nonEmpty) sys.error("the gate must carry no other axis")

  if (job.name != "Custom name") sys.error(s"name not honoured: ${job.name}")

  val fail = job.steps.head
  if (!fail.cond.exists(c => c.contains("'failure'") && c.contains("'cancelled'")))
    sys.error(s"must fail on failure and cancellation: ${fail.cond}")
  if (fail.cond.exists(_.contains("skipped")))
    sys.error(s"a skipped dependency is not a failure: ${fail.cond}")
}

ThisBuild / checkOff := Def.uncached {
  if ((ThisBuild / githubWorkflowGeneratedCI).value.exists(_.id == "required-checks"))
    sys.error("generated a gate while disabled")
}
