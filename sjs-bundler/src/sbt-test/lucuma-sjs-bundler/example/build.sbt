lazy val foo = project
  .in(file("modules/foo"))
  .enablePlugins(LucumaSJSBundlerPlugin)
  .settings(
    Test / test := {
      val deps    = (Compile / npmDependencies).value.sorted
      val devDeps = (Compile / npmDevDependencies).value.sorted

      val expectedDeps = Seq(
        "react"             -> "^17.0.2",
        "react-dom"         -> "^17.0.2",
        "fomantic-ui-less"  -> "^2.8.7",
        "semantic-ui-react" -> "^2.0.3"
      ).sorted

      val expectedDevDeps = Seq(
        "vite"                -> "^2.1.0",
        "less"                -> "3.9.0",
        "less-watch-compiler" -> "1.14.6"
      ).sorted

      require(deps == expectedDeps)
      require(devDeps == expectedDevDeps)
    }
  )
