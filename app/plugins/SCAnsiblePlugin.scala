package plugins

import scala.sys.process._

abstract class SCAnsiblePlugin extends Plugin {

  protected def checkUvInstalled(): Boolean = {
    val uvVersion = "uv --version"
    try {
      val output: String = uvVersion.!!
      if (output.startsWith("uv")) {
        // uv was found
        true
      } else {
        false
      }
    } catch {
      case _: Throwable => false
    }
  }

  protected def checkScansiblePluginInstalled: Boolean = {
    val scansiblePluginFolder = getScansiblePluginFolder
    scansiblePluginFolder.isDefined
  }

  override def checkPlugin(): Option[String] = {
    val uvInstalled = checkUvInstalled()
    if (! uvInstalled) {
      Some("""Required package 'uv' not found in the environment. Please consult the documentation for the SCAnsible plugin for information on setting up the environment.""")
    } else {
      val scansiblePluginInstalled = checkScansiblePluginInstalled
      if (! scansiblePluginInstalled) {
        Some("""The 'plugins' folder does not contain a 'scansible' folder. Did you make sure to download and extract 'scansible' to the 'plugins' folder?""")
      } else {
        None
      }
    }
  }

}
