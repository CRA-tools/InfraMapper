package plugins

import scala.sys.process._

abstract class SCAnsiblePlugin extends Plugin {

  override def checkPlugin(): Option[String] = {
    val incorrectSetupMessage: String =
      """Required package 'uv' not found in the environment. Please consult the documentation for the SCAnsible plugin for information on setting up the environment."""
    val uvVersion = "uv --version"
    try {
      val output: String = uvVersion.!!
      if (output.startsWith("uv")) {
        None
      } else {
        Some(incorrectSetupMessage)
      }
    } catch {
      case _: Exception =>
        Some(incorrectSetupMessage)
    }
  }

}
