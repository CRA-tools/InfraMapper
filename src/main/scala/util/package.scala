import os.Path

import java.io.File
import java.net.{HttpURLConnection, URL}
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

}
