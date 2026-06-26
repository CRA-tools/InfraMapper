package app

import scopt._

import config._

class ArgsParser(private val appName: String) {

  protected val parser: scopt.OptionParser[StartupConfiguration] = new scopt.OptionParser[StartupConfiguration](appName) {
    head(appName, "0.2")
    help("help") text "Prints this usage text."
    cmd("dependencies") action { (_, c) =>
      c.copy(command = FetchDependenciesCommand, extraCommandOptions = FetchDependenciesCommand.defaultOptions)
    } text FetchDependenciesCommand.description children(
      opt[Unit]("vulnerabilities") action { (x, c) =>
        c.copy(extraCommandOptions = c.extraCommandOptions.asInstanceOf[FetchDependenciesOptions].copy(checkForVulnerabilities = true))
      } text "Analyse the dependencies for known vulnerabilities.",
      arg[String]("project") action { (x, c) =>
        c.copy(input = x)
      } text "Path to the project folder containing the Ansible playbook"
    )
    cmd("weaknesses") action { (_, c) =>
      c.copy(command = AnalyseWeaknessesCommand, extraCommandOptions = AnalyseWeaknessesCommand.defaultOptions)
    } text AnalyseWeaknessesCommand.description children(
      arg[String]("playbook") action { (x, c) =>
        c.copy(input = x)
      } text "Path to the IaC playbook",
      )
  }

  def usage: String = parser.usage

  def parseArgs(args: Array[String]): Option[StartupConfiguration] = {
    val defaultConfig = StartupConfiguration(FetchDependenciesCommand, "", FetchDependenciesCommand.defaultOptions)
    val result = parser.parse(args, defaultConfig)
    if (result.isEmpty) {
      println(parser.usage)
    }
    result
  }

}
