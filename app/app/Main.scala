package app

import config.*
import plugins.SCAnsiblePlugin
import plugins.dependencies.DependenciesPlugin
import plugins.weaknesses.WeaknessesPlugin
import util.Log

object Main {

  val appName: String = "InfraMapper"

  def run(config: StartupConfiguration): Either[String, Unit] = {
    val maybeEngine: Either[String, SCAnsiblePlugin] = config.command match {
      case AnalyseWeaknessesCommand =>
        if (! config.extraCommandOptions.isInstanceOf[AnalyseWeaknessesOptions]) {
          val message = s"Incompatible options ${config.extraCommandOptions} for command ${config.command}"
          Log.logError(message)
          Left(message)
        } else {
          Right(new WeaknessesPlugin(config.input, config.extraCommandOptions.asInstanceOf[AnalyseWeaknessesOptions]))
        }
      case FetchDependenciesCommand =>
        if (! config.extraCommandOptions.isInstanceOf[FetchDependenciesOptions]) {
          val message = s"Incompatible options ${config.extraCommandOptions} for command ${config.command}"
          Left(message)
        } else {
         Right(new DependenciesPlugin(config.input, config.extraCommandOptions.asInstanceOf[FetchDependenciesOptions]))
        }
    }
    maybeEngine match {
      case Left(message) => Left(message)
      case Right(engine) => Right(engine.run())
    }
  }

  def main(args: Array[String]): Unit = {
    val parser = new ArgsParser(appName)
    val optConfig = parser.parseArgs(args)
    optConfig match {
      case None =>
        System.exit(1)
      case Some(config) => run(config)
    }
  }
}

