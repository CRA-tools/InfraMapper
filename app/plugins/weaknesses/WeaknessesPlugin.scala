package plugins.weaknesses

import io.circe.Json
import java.io.File
import scala.jdk.CollectionConverters._

import config.AnalyseWeaknessesOptions
import plugins.{SCAnsiblePlugin, getScansiblePluginFolder}
import util.getAbsolutePath


class WeaknessesPlugin(file: String, config: AnalyseWeaknessesOptions) extends SCAnsiblePlugin {

  override type PluginResult = String

  protected def constructCommand(): String = {
    val pathToFile = getAbsolutePath(file)
    s"""uv run scansible check --enable-security $pathToFile"""
  }

  protected def weaknessToDescription(weakness: String): String = weakness match {
    case "AdminByDefault" => "Avoid using admin accounts, as this violates the principle of least privileges"
    case "EmptyPassword" => "Never use empty passwords, these are easy to crack"
    case "HardcodedSecret" => "Hardcoded secrets can compromise security when the source code falls into the wrong hands"
    case "HTTPWithoutSSLTLS" => "Always use SSL/TLS to connect over HTTP, i.e., use HTTPS"
    case "MissingIntegrityCheck" => "The integrity of source code needs to be checked with cryptographic hashes after downloading"
    case "UnrestrictedIPAddress" => "Do not bind to the 0.0.0.0 address, as this exposes the service to the entire Internet"
    case "WeakCryptoAlgorithm" => "Do not use weak cryptographic algorithms like CRC32, MD5, or SHA-1. Use SHA-256 or stronger instead."
    case _ => "No description available"
  }

  override def resultToOutput(result: PluginResult): Json = {
    def tupleToJsonObject(tuple: (String, Int, Int, String)): Json = {
      Json.obj("line number" -> Json.fromInt(tuple._2),
        "column number" -> Json.fromInt(tuple._3),
        "weakness" -> Json.fromString(tuple._4),
        "description" -> Json.fromString(weaknessToDescription(tuple._4))
      )
    }
    val lines = result.split('\n')
    // Split each line into a tuple of (file_name, line number, column number, weakness)
    val splitLines: List[(String, Int, Int, String)] = lines.toList.flatMap(line => {
      val regex = """(.*):([0-9]+):([0-9]+)\s-\s(.*)""".r
      line match {
        case regex(fileName, lineNumberString, columnNumberString, weakness) =>
          try {
            val lineNumber = Integer.valueOf(lineNumberString)
            val columnNumber = Integer.valueOf(columnNumberString)
            List((fileName, lineNumber, columnNumber, weakness))
          } catch {
            case _: NumberFormatException => Nil
          }
        case _ => Nil
      }
    })
    val converted: List[Json] = splitLines.map(tupleToJsonObject)
    Json.fromValues(converted)
  }

  override def internalRun(): Either[String, PluginResult] = {
    try {
      val command: String = constructCommand()
      val scansibleDirectory = getScansiblePluginFolder.get // Should exist, because otherwise the 'checkPlugin' test should have failed
      val pb = new java.lang.ProcessBuilder(scansibleDirectory.toString)
      pb.directory(new File(scansibleDirectory.toString))
      val withCommand = pb.command(constructCommand().split(' ').toList.asJava)
      val process = pb.start()
      val result = new String(process.getInputStream.readAllBytes)
      Right(result)
    } catch {
      case t: Throwable => Left(t.getMessage)
    }
  }
}
