package plugins.weaknesses

import config.AnalyseWeaknessesOptions
import plugins.{SCAnsiblePlugin, getScansiblePluginFolder}
import util.getAbsolutePath

import java.io.File
import scala.sys.process.*

class WeaknessesPlugin(file: String, config: AnalyseWeaknessesOptions) extends SCAnsiblePlugin {
  protected def constructCommand(): String = {
    val pathToFile = getAbsolutePath(file)
    s"""uv run scansible check --enable-security ../../$file"""
  }

  override def internalRun(): Unit = {
    try {
      val command: String = constructCommand()
      val scansibleDirectory = getScansiblePluginFolder.get // Should exist, because otherwise the 'checkPlugin' test should have failed
      val pb = new java.lang.ProcessBuilder(scansibleDirectory.toString)
      pb.directory(new File(scansibleDirectory.toString))
      println(s"Working directory is ${pb.directory()}")
      import scala.collection.JavaConverters._
      val withCommand = pb.command(constructCommand().split(' ').toList.asJava)
      try {
        val process = withCommand.run()
        while (process.isAlive()) {}
      } catch {
        case t: Throwable =>
          println(t)
      }
    } catch {
      case _: Throwable =>
    }
  }
}
