enablePlugins(LucumaSJSBundlerPlugin)

Test / test := {
  val deps    = (Compile / npmDependencies).value.sorted
  val devDeps = (Compile / npmDevDependencies).value.sorted

  val expectedDeps = Seq(
    "react"     -> "^17.0.2",
    "react-dom" -> "^17.0.2"
  ).sorted

  val expectedDevDeps = Seq(
    "vite" -> "^2.1.0"
  ).sorted

  require(deps == expectedDeps)
  require(devDeps == expectedDevDeps)
}
