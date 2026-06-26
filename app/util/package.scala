import os.Path

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path => JPath}
import scala.sys.process.urlToProcess

package object util {

  def getAbsolutePath(path: String): Path = {
    Path.apply(path, getInfraMapperHomeOrPwd)
  }

  def getInfraMapperHomeOrPwd: Path = {
    try {
      val home: String = Environment.getInfraMapperHome
      Path.apply(home)
    } catch {
      case t: Throwable => os.pwd
    }
  }

  def removeOptFile(path: String): Unit = {
    try {
      Files.deleteIfExists(JPath.of(path))
    } catch {
      case _: Throwable =>
    }
  }

}
