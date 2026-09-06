lazy val a = project.in(file("a"))

ThisBuild / githubWorkflowAddedJobs += lucumaAffectedJob(
  WorkflowJob("deploy", "Deploy", List(WorkflowStep.Run(List("echo deploying"))), cond = Some("github.ref == 'refs/heads/main'")),
  "a"
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
  if (!affected.steps.flatMap(_.name).contains("Compute affected projects"))
    sys.error(s"affected job has no report step: ${affected.steps.flatMap(_.name)}")

  if (!deploy.needs.contains("affected")) sys.error(s"deploy needs ${deploy.needs}")

  val cond = deploy.cond.getOrElse(sys.error("deploy has no cond"))
  // the job's own condition survives, ANDed with the gate
  if (!cond.contains("refs/heads/main")) sys.error(s"lost the original cond: $cond")
  if (!cond.contains("contains(fromJSON(needs.affected.outputs.projects), 'a')"))
    sys.error(s"not gated on `a`: $cond")
}

ThisBuild / checkNoJob := {
  val all = (ThisBuild / githubWorkflowGeneratedCI).value
  if (all.exists(_.id == "affected"))
    sys.error("generated an `affected` job that nothing depends on")
}
