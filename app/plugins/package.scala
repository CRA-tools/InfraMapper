import os.Path

package object plugins {

  def getScansiblePluginFolder: Option[Path] = {
    val scansiblePluginFolder1 = os.pwd / "plugins/scansible"
    val scansiblePluginFolder2 = os.pwd / "plugins/scansible-main"
    if (os.isDir(scansiblePluginFolder1)) {
      Some(scansiblePluginFolder1)
    } else if (os.isDir(scansiblePluginFolder2)) {
      Some(scansiblePluginFolder2)
    } else {
      None
    }
  }

}
