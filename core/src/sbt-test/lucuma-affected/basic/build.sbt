// The git plumbing is exercised against a real repository elsewhere; here we feed the diff in
// directly so the test can focus on how files map onto projects and dependents.
ThisBuild / lucumaAffectedChangedFiles := Some(IO.readLines(file("changed.txt")).filter(_.nonEmpty))

lazy val checkAffected = inputKey[Unit]("Assert the affected project set")

ThisBuild / checkAffected := {
  val expected = sbt.complete.DefaultParsers.spaceDelimited("<project>").parsed.sorted
  val actual   = lucumaAffectedProjects.value.sorted
  if (actual != expected)
    sys.error(s"expected [${expected.mkString(", ")}] but got [${actual.mkString(", ")}]")
}

lazy val a = project.in(file("a"))

lazy val b = project.in(file("b")).dependsOn(a)

lazy val c = project.in(file("c"))

// stands in for a crossProject: sources live outside the project's base directory
lazy val x = project
  .in(file("x"))
  .settings(
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala"
  )
