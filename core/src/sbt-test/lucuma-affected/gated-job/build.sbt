lazy val a = project.in(file("a"))
lazy val b = project.in(file("b"))

ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("deploy", "Deploy", List(WorkflowStep.Run(List("echo deploying"))), cond = Some("github.ref == 'refs/heads/main'")),
  a
)

// several projects: any one of them is enough
ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("bundle", "Bundle", List(WorkflowStep.Run(List("echo bundling")))),
  a,
  b
)

lazy val checkGate    = taskKey[Unit]("The deploy job is gated and the affected job exists")
lazy val checkNoJob   = taskKey[Unit]("No affected job when nothing gates on it")

def jobs = Def.task((ThisBuild / githubWorkflowGeneratedCI).value)

ThisBuild / checkGate := {
  val all      = (ThisBuild / githubWorkflowGeneratedCI).value
  val affected = all.find(_.id == "affected").getOrElse(sys.error("no `affected` job"))
  val deploy   = all.find(_.id == "deploy").getOrElse(sys.error("no `deploy` job"))

  if (affected.outputs.get("projects") != Some("steps.report.outputs.projects"))
    sys.error(s"unexpected outputs: ${affected.outputs}")
  val report = affected.steps
    .find(_.name.contains("Compute affected projects"))
    .getOrElse(sys.error(s"no report step: ${affected.steps.flatMap(_.name)}"))

  // a merge to main is measured against the previous main, not treated as "everything"
  val pushBase = report.env.get("LUCUMA_AFFECTED_PUSH_BASE")
  if (pushBase != Some("${{ github.event.before }}"))
    sys.error(s"report step has no push base ref: ${report.env}")

  if (!deploy.needs.contains("affected")) sys.error(s"deploy needs ${deploy.needs}")

  // the job's own condition is left alone, so its matrix still expands and the check keeps the
  // name branch protection is looking for
  val cond = deploy.cond.getOrElse(sys.error("deploy has no cond"))
  if (cond.contains("needs.affected")) sys.error(s"the job itself must not be gated: $cond")
  if (!cond.contains("refs/heads/main")) sys.error(s"lost the original cond: $cond")

  // a green job that did nothing says so, and only then
  val announce = deploy.steps.head
  if (!announce.name.contains("Report that nothing was affected"))
    sys.error(s"no skip announcement: ${deploy.steps.flatMap(_.name)}")
  if (announce.cond != Some("!(contains(fromJSON(needs.affected.outputs.projects), 'a'))"))
    sys.error(s"announcement must fire only when unaffected: ${announce.cond}")

  // the gate lives on the real steps
  val step = deploy.steps(1)
  if (!step.cond.exists(_.contains("contains(fromJSON(needs.affected.outputs.projects), 'a')")))
    sys.error(s"step not gated on `a`: ${step.cond}")

  val bundle = all.find(_.id == "bundle").getOrElse(sys.error("no `bundle` job"))
  val expected = Some(
    "(contains(fromJSON(needs.affected.outputs.projects), 'a')" +
      " || contains(fromJSON(needs.affected.outputs.projects), 'b'))"
  )
  if (bundle.steps(1).cond != expected)
    sys.error(s"expected $expected but got ${bundle.steps(1).cond}")
}

lazy val checkNoDuplicate = taskKey[Unit]("A hand-written affected job is not doubled")

ThisBuild / checkNoDuplicate := {
  val ids = (ThisBuild / githubWorkflowGeneratedCI).value.map(_.id)
  if (ids.count(_ == "affected") != 1)
    sys.error(s"duplicate or missing `affected` job: ${ids.mkString(", ")}")
}

ThisBuild / checkNoJob := {
  val all = (ThisBuild / githubWorkflowGeneratedCI).value
  if (all.exists(_.id == "affected"))
    sys.error("generated an `affected` job that nothing depends on")
}
