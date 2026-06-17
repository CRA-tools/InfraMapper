package util

import os.Path

object Environment {
  
  val InfraMapperHomeVar: String = "INFRAMAPPER_HOME"
  
  def getInfraMapperHome: String = {
    try {
      System.getenv(InfraMapperHomeVar)
    } catch {
      case _: NullPointerException =>
        val message = s"Environment variable $InfraMapperHomeVar not set, aborting"
        Log.logError(message)
        throw ConfigurationException(message)
    }
  }
}
