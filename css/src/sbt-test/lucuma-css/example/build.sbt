ThisBuild / version      := sys.props("plugin.version")
ThisBuild / organization := "sbt-lucuma"

lazy val foo = project
  .in(file("modules/foo"))
  .enablePlugins(ScalaJSPlugin)

lazy val bar = project
  .in(file("modules/bar"))
  .enablePlugins(ScalaJSPlugin)

lazy val baz = project
  .in(file("modules/baz"))
  .enablePlugins(ScalaJSPlugin, LucumaCssPlugin)
  .dependsOn(bar) // depend on a local classpath
  .settings(      // depend on a jar
    libraryDependencies += organization.value %%% "foo" % version.value
  )
