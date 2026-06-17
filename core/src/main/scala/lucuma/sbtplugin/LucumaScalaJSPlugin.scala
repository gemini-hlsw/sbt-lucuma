// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sbtplugin

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.*

import Keys.*

object LucumaScalaJSPlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  override def trigger = allRequirements

  override def projectSettings = Seq(
    evictionErrorLevel := Level.Warn,
    // MUnit reads MUNIT_FLAKY_OK via System.getenv at test runtime, which always returns
    // empty on Scala.js -- so flaky tests cannot be honored at runtime there. When the env
    // var is set, instead exclude flaky-tagged tests on Scala.js at the build level (this is
    // evaluated here in the sbt JVM via sys.env and passed to MUnit as a test argument). This
    // plugin requires ScalaJSPlugin, so it only activates on the JS project of a crossProject;
    // the JVM side keeps MUnit's normal runtime MUNIT_FLAKY_OK behavior.
    Test / testOptions ++= {
      if (sys.env.get("MUNIT_FLAKY_OK").contains("true"))
        Seq(Tests.Argument(new TestFramework("munit.Framework"), "--exclude-tags=Flaky"))
      else
        Nil
    }
  )

}
