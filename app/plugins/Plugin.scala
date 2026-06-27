package plugins

import io.circe.Json
import util.Log

trait Plugin {

  type PluginResult

  protected def internalRun(): Either[String, PluginResult]
  def checkPlugin(): Option[String]
  def resultToOutput(result: PluginResult): Json

  def run(): Either[String, PluginResult] = {
    val optCheck = checkPlugin()
    optCheck match {
      case None => internalRun()
      case Some(message) =>
        Log.logError(message)
        Left(message)
    }
  }
}
