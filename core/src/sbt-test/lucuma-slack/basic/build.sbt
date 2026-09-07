ThisBuild / lucumaSlackNotify := true
ThisBuild / lucumaSlackNotifyWorkflows += "Nightly"

lazy val a = project.in(file("a"))

lazy val checkGenerated = taskKey[Unit]("The workflow was written and reflects the settings")
lazy val checkInCi      = taskKey[Unit]("The check runs in CI alongside the other config checks")

ThisBuild / checkGenerated := {
  val f = (ThisBuild / baseDirectory).value / ".github" / "workflows" / "ci-failure-slack.yml"
  if (!f.exists) sys.error(s"$f was not written")
  val text = IO.read(f)
  List(
    "workflows: [Continuous Integration, Nightly]",
    "branches: [main]",
    "continue-on-error: true",
    "secrets.GPP_SLACK_WEBHOOK_URL"
  ).foreach(s => if (!text.contains(s)) sys.error(s"missing from the workflow: $s"))
}

ThisBuild / checkInCi := {
  val header = (ThisBuild / githubWorkflowBuild).value.collectFirst {
    case s: WorkflowStep.Sbt if s.name.exists(_.contains("Check headers")) => s.commands
  }.getOrElse(sys.error("no header-check step"))
  if (!header.contains("lucumaSlackNotifyCheck"))
    sys.error(s"lucumaSlackNotifyCheck is not wired into CI: $header")
}
