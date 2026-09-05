// A Scala.js cross build, so TypelevelCiJSPlugin is active. It inserts its `scalaJSLink` step by
// matching the test step on `commands == List("test")`, which means anything that rewrites that
// command too early makes the step vanish.

lazy val root = tlCrossRootProject.aggregate(model)

lazy val model = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("model"))

lazy val checkWorkflow = inputKey[Unit]("Assert the generated build job's steps")

ThisBuild / checkWorkflow := {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("<command>").parsed.head
  val build    = (ThisBuild / githubWorkflowGeneratedCI).value
    .find(_.id == "build")
    .getOrElse(sys.error("no `build` job"))
  val names    = build.steps.flatMap(_.name)

  if (!names.contains("scalaJSLink"))
    sys.error(s"scalaJSLink was dropped; steps are ${names.mkString(", ")}")

  val test = build.steps.collect { case s: WorkflowStep.Sbt if s.commands == List(expected) => s }
  if (test.size != 1) sys.error(s"expected one `$expected` step, steps are ${names.mkString(", ")}")

  val link = names.indexOf("scalaJSLink")
  val idx  = names.indexOf(test.head.name.get)
  if (link > idx) sys.error("scalaJSLink must still come before the test step")
}
