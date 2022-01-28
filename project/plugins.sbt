Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala"

addSbtPlugin("org.typelevel"             % "sbt-typelevel"  % "0.4.3")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"   % "0.9.33")
addSbtPlugin("com.timushev.sbt"          % "sbt-rewarn"     % "0.1.3")
