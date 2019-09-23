// Use this project as its own plugin
unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("com.geirsson"              % "sbt-ci-release" % "1.4.31")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"     % "5.2.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.1.8")

