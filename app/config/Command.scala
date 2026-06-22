package config

trait Command {
  def name: String
  def description: String
  def defaultOptions: CommandOptions
}

trait CommandOptions

case class FetchDependenciesOptions(checkForVulnerabilities: Boolean) extends CommandOptions

case object FetchDependenciesCommand extends Command {
  override def name: String = "dependencies"
  override def description: String = "Fetch and analyse the dependencies required by the IaC."
  override def defaultOptions: FetchDependenciesOptions = FetchDependenciesOptions(false)
}

case class AnalyseWeaknessesOptions() extends CommandOptions

case object AnalyseWeaknessesCommand extends Command {
  override def name: String = "weaknesses"
  override def description: String = "Scan for security weaknesses and smells introduced by the IaC."
  override def defaultOptions: AnalyseWeaknessesOptions = AnalyseWeaknessesOptions()
}