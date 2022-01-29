addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.9.34")
addSbtPlugin("com.timushev.sbt"   % "sbt-rewarn"               % "0.1.3")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.4.6")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.6.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.8.0")

val sbtTypelevelVersion = "0.4.3"
addSbtPlugin("org.typelevel"      % "sbt-typelevel-ci"         % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"      % "sbt-typelevel-github"     % sbtTypelevelVersion)
addSbtPlugin("org.typelevel"      % "sbt-typelevel-settings"   % sbtTypelevelVersion)
