ThisBuild / lucumaTestShards := 4

lazy val a = project.in(file("a"))

lazy val checkShardMatrix = taskKey[Unit]("The build matrix gained a shard axis")
lazy val checkTestStepEnv = taskKey[Unit]("The test step carries the shard env")
lazy val checkFailFast    = taskKey[Unit]("Shards don't cancel each other")

ThisBuild / checkShardMatrix := {
  val actual = (ThisBuild / githubWorkflowBuildMatrixAdditions).value.get("shard")
  val expected = Some(List("0", "1", "2", "3"))
  if (actual != expected) sys.error(s"expected $expected but got $actual")
}

ThisBuild / checkTestStepEnv := {
  val envs = (ThisBuild / githubWorkflowBuild).value.collect {
    case s: WorkflowStep.Sbt if s.commands.exists(_.startsWith("lucumaTestAffected")) => s.env
  }
  val expected = Map("TEST_SHARD" -> "${{ matrix.shard }}", "TEST_SHARD_COUNT" -> "4")
  if (envs != List(expected)) sys.error(s"expected one step with $expected but got $envs")
}

ThisBuild / checkFailFast := {
  val actual = (ThisBuild / githubWorkflowBuildMatrixFailFast).value
  if (actual != Some(false)) sys.error(s"expected Some(false) but got $actual")
}
