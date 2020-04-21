// Use this project as its own plugin
unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("com.geirsson"              % "sbt-ci-release" % "1.5.3")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"     % "5.5.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.1.11")

