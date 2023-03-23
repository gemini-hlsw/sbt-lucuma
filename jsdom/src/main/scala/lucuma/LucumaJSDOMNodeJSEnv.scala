// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma

import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import org.scalajs.jsenv._
import sbt.io.IO
import sbt.io.syntax._

import LucumaJSDOMNodeJSEnv._

class LucumaJSDOMNodeJSEnv extends JSDOMNodeJSEnv {
  override def start(
    input:     Seq[Input],
    runConfig: RunConfig
  ): JSRun = super.start(globals +: hackInput(input), runConfig)

  override def startWithCom(
    input:     Seq[Input],
    runConfig: RunConfig,
    onMessage: String => Unit
  ): JSComRun = super.startWithCom(globals +: hackInput(input), runConfig, onMessage)

  def hackInput(input: Seq[Input]) =
    input.map {
      case Input.CommonJSModule(module) => Input.Script(module)
      case other                        => other
    }
}

object LucumaJSDOMNodeJSEnv {
  private val globals = {
    val tmp = IO.createTemporaryDirectory / "lucuma.js"
    IO.write(
      tmp,
      """|const outerRealmFunctionConstructor = Node.constructor;
         |const nodeGlobal = new outerRealmFunctionConstructor("return global")();
         |nodeGlobal.document = document;
         |nodeGlobal.navigator = navigator;
         |nodeGlobal.window = window;
         |window.require = new outerRealmFunctionConstructor("return require")();
         |""".stripMargin
    )
    Input.Script(tmp.toPath)
  }
}
