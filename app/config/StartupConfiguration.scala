package config

import config.Command

case class StartupConfiguration(command: Command, input: String, extraCommandOptions: CommandOptions)
