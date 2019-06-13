// Use this project as its own plugin
unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("com.geirsson"       % "sbt-ci-release" % "1.2.6")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"     % "5.2.0")

