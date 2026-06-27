package plugins.dependencies

import os.Path
import io.circe.*
import io.circe.parser.*

import scala.collection.immutable.Map
import scala.sys.process.*
import sttp.client4.quick.*
import sttp.client4.{Response, SttpClientException}
import sttp.model.Uri
import config.FetchDependenciesOptions
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import plugins.{SCAnsiblePlugin, getScansiblePluginFolder}
import plugins.dependencies.Vulnerability
import util.{Log, getAbsolutePath}

import java.io.File

object EcosystemsSeverityWarning {
  def translateWarning(warning: String): String = {
    if (warning == "MODERATE") {
      "MEDIUM"
    } else {
      warning
    }
  }
}

object DebianNameMappings {
  def translateName(name: String): String = name match {
    case "docker" => "docker.io"
    case "python" => "python3.12"
    case _ => name
  }
}

class EcoSystemsCache(protected val cacheFolderPath: Path) {

  private var cache: Map[String, Json] = Map()
  private val cachePath: Path = cacheFolderPath / "ecosystems_cache.json"

  protected implicit val decoder: Decoder[Json] = deriveDecoder[Json]
  protected implicit val encoder: Encoder[Json] = deriveEncoder[Json]

  if (os.isFile(cachePath)) {
    readCache()
  }

  protected def readCache(): Unit = {
    val cacheText: String = os.read(cachePath)
    val optContent = parse(cacheText)
    optContent match {
      case Left(error) =>
        System.err.println(s"ERROR: Could not parse cache content at $cachePath")
        System.exit(1)
      case Right(content) =>
        content.asObject match {
          case None =>
            System.err.println(s"ERROR: Could not parse cache content at $cachePath as a dictionary")
            System.exit(1)
          case Some(dict) =>
            cache = dict.toMap
        }
    }
  }

  protected def writeCache(): Unit = {
    val obj = JsonObject.fromMap(cache)
    if (os.exists(cachePath)) {
      os.remove(cachePath)
    }
    os.write(cachePath, obj.toJson.spaces2)
  }

  protected def paramsToKey(ecosystem: String, pack: String): String =
    s"$ecosystem:$pack"

  def contains(key: (String, String)): Boolean = {
    cache.contains(paramsToKey(key._1, key._2))
  }

  def set(ecosystem: String, pack: String, value: Json): Unit = {
    cache += paramsToKey(ecosystem, pack) -> value
    writeCache()
  }

  def get(ecosystem: String, pack: String): Option[Json] = {
    cache.get(paramsToKey(ecosystem, pack))
  }

}

sealed trait DependenciesResult
case class DependenciesWithVulnerabilities(vulnerabilitiesScanned: VulnerabilitiesScanned) extends DependenciesResult
case class DependenciesWithoutVulnerabilities(json: Json) extends DependenciesResult

class DependenciesPlugin(input: String, config: FetchDependenciesOptions) extends SCAnsiblePlugin {

  override type PluginResult = DependenciesResult

  protected val cacheFolderPath: Path = os.pwd / "cache"
  protected val ecoSystemsCache = new EcoSystemsCache(cacheFolderPath)
  protected var _debian_advisories_cache: Option[Map[String, JsonObject]] = None

  protected def raiseForStatus(response: Response[String], uri: Uri): Unit = {
    if (response._2.code != 200) {
      System.err.println(s"ERROR: Could not reach $uri")
      System.exit(1)
    }
  }

  protected def getDebianAdvisories(): Map[String, JsonObject] = {
    if (_debian_advisories_cache.isDefined) {
      _debian_advisories_cache.get
    } else {
      val adv_path = cacheFolderPath / "debian_advisories.json"
      if (!os.isFile(adv_path)) {
        val url = uri"https://security-tracker.debian.org/tracker/data/json"
        val resp: Response[String] = quickRequest.get(url).send()
        raiseForStatus(resp, url)
        os.write(adv_path, resp._1)
      }
      val advFileContent = os.read(adv_path)
      val optAdvJson = parse(advFileContent)
      optAdvJson match {
        case Left(error) =>
          System.err.println(s"ERROR: Could not parse cache content at $adv_path")
          System.exit(1)
          throw new Exception()
        case Right(advJson) =>
          advJson.asObject match {
            case None =>
              System.err.println(s"ERROR: Could not parse cache content at $adv_path as a dictionary")
              System.exit(1)
              throw new Exception()
            case Some(advJsonObject) =>
              val convertedAdvJsonObject: Map[String, JsonObject] = advJsonObject.toMap.map((k, v) => v.asObject match {
                case None =>
                  System.err.println(s"ERROR: Could not parse json value $v as a dictionary")
                  System.exit(1)
                  throw new Exception()
                case Some(obj) => (k, obj)
              })
              _debian_advisories_cache = Some(convertedAdvJsonObject)
              convertedAdvJsonObject
          }
      }
    }
  }

  protected def find_debian_vulnerabilities(package_name: String): List[Vulnerability] = {
    val debian_advisories = getDebianAdvisories()
    val pkg_name_debian = DebianNameMappings.translateName(package_name)

    val cves: JsonObject = debian_advisories.getOrElse(pkg_name_debian, debian_advisories.getOrElse("lib" + pkg_name_debian, JsonObject.fromMap(Map())))
    cves.toList.filter((cve, content) => ! cve.startsWith("TEMP-")).map((cve, content) => build_vuln_from_debian(package_name, cve, content.asObject.get))
  }

  protected def find_pypi_vulnerabilities(packageName: String): List[Vulnerability] = {
    val optPackageMeta: Option[Json] = searchEcosystems(ecosystem = "pypi.org", packageName)
    if (optPackageMeta.isEmpty) {
      println(s"${Console.YELLOW_B}WARNING:${Console.RESET} Could not resolve package $packageName")
      List()
    } else {
      val packageMeta: Json = optPackageMeta.get
      val optContentList: Option[List[Json]] = packageMeta.asObject.get.toMap.apply("advisories").asArray.map(_.toList)
      val contentList: List[Json] = optContentList.getOrElse(List())
      contentList.map((adv: Json) => _build_vuln_from_ecosystems(packageName, adv.asObject.get))
    }
  }

  def findVulnerabilities(packageName: String, packageType: String): List[Vulnerability] = {
    if (packageType == "OS")
      find_debian_vulnerabilities(packageName)
    else
      find_pypi_vulnerabilities(packageName)
  }

  protected def build_vuln_from_debian(package_name: String, cve: String, content: JsonObject): Vulnerability = {
    var severity: String = content.toMap.apply("releases").asObject.get.toMap.apply("bookworm").asObject.get.toMap.apply("urgency").asString.get
    if (severity == "not yet assigned") {
      severity = "unknown"
    }
    val description: String = content.toMap.apply("description").asString.getOrElse("")
    Vulnerability(package_name, cve, description.take(100), severity, description)
  }


  protected def _build_vuln_from_ecosystems(package_name: String, adv: JsonObject): Vulnerability = {
    val title: String = adv.toMap.get("title").map((value: Json) => value.toString).getOrElse("")
    val severity: String = adv.toMap("severity").toString
    val description: String = adv.toMap("description").toString
    Vulnerability(package_name, _get_ecosystems_id(adv), title, EcosystemsSeverityWarning.translateWarning(severity).toLowerCase, description)
  }

  protected def _get_ecosystems_id(adv: JsonObject): String = {
    val listOfIdentifiers = adv.toMap("identifiers").asArray.get.toList
    val converted = listOfIdentifiers.map(_.asString.get)
    val optFound = converted.find(ident => ident.startsWith("CVE-"))
    optFound match {
      case Some(found) => found
      case None => converted.head
    }
  }

  protected def searchEcosystems(ecosystem: String, pack: String): Option[Json] = {
    if (ecoSystemsCache.contains((ecosystem, pack))) {
      ecoSystemsCache.get(ecosystem, pack)
    } else {
      try {
        val uri: sttp.model.Uri = uri"https://packages.ecosyste.ms/api/v1/registries/$ecosystem/packages/$pack"
        val resp: Response[String] = quickRequest.get(uri).send()
        if (resp._2.code == 404) {
          None
        } else {
          raiseForStatus(resp, uri)
          parse(resp._1) match {
            case Left(error) =>
              System.err.println(s"ERROR: Could not parse cache content at ${uri.toString}")
              System.exit(1)
              throw new Exception
            case Right(content) =>
              ecoSystemsCache.set(ecosystem, pack, content)
              Some(content)
          }
        }
      } catch {
        case t: SttpClientException =>
          None
      }
    }
  }

  protected def extractDependenciesCommand(dependenciesOutputPath: String): String = {
    val inputAbsolutePath = getAbsolutePath(input).toNIO
    val dependenciesOutputAbsolutePath = getAbsolutePath(dependenciesOutputPath).toNIO
    s"""uv run scansible extract-dependencies $inputAbsolutePath $dependenciesOutputPath"""
  }

  override def checkPlugin(): Option[String] = {
    val previousCheck = super.checkPlugin()
    previousCheck match {
      case None =>
        // Previous check did not fail, check whether a scansible plugin folder can be found
        val scansiblePluginFolder = getScansiblePluginFolder
        scansiblePluginFolder match {
          case None => Some("The 'plugins' folder does not contain a 'scansible' folder. Did you make sure to download and extract 'scansible' to the 'plugins' folder?")
          case _ => None
        }
      case _ => previousCheck
    }
  }

  protected def extractDependenciesWithScansible(): (String, Path) = {
    val tempOutputFolder = getAbsolutePath("tmp")
    if (!os.isDir(tempOutputFolder)) {
      os.makeDir(tempOutputFolder)
    }
    val tempOutput = tempOutputFolder / "out.json"
    if (os.isFile(tempOutput)) {
      os.remove(tempOutput)
    }
    val command: String = extractDependenciesCommand(tempOutput.toString)
    val scansibleDirectory = getScansiblePluginFolder.get // Should exist, because otherwise the 'checkPlugin' test should have failed
    val process = Process(command, new File(scansibleDirectory.toString))
    try {
      val result = process.!!
      (result, tempOutput)
    } catch {
      case e: java.lang.RuntimeException =>
        System.err.println("ERROR: scansible failed. Aborting")
        System.exit(2)
        throw e
    }
  }

  protected def printSingleVulnerability(vuln: Vulnerability): Unit = {
    println(s"\t\t${Console.BOLD}${Console.RED}${vuln.id}${Console.RESET} (severity: ${vuln.severity}): ${vuln.summary}")
  }

  protected def printVulnerabilities(dependencyName: String, vulnerabilities: List[Vulnerability]): Unit = {
    val nrOfVulns = vulnerabilities.length
    if (nrOfVulns == 0) {
      println(s"$dependencyName: No known CVEs.")
    } else {
      println(s"$dependencyName: Possibly affected by ${Console.BOLD}${Console.BLUE}$nrOfVulns${Console.RESET} CVEs. Most recent:")
      vulnerabilities.foreach(printSingleVulnerability)
    }
  }

  override def resultToOutput(result: DependenciesResult): Json = {
    def vulnerabilityToJson(vuln: Vulnerability): Json = {
      Json.obj("package name" -> Json.fromString(vuln.packageName),
        "CVE id" -> Json.fromString(vuln.id),
        "CVE severity" -> Json.fromString(vuln.severity),
        "summary" -> Json.fromString(vuln.summary))
    }
    def tupleToJson(vuln: (String, String, List[Vulnerability])): Json = {
      var map = Map[String, Json](
        "name" -> Json.fromString(vuln._1),
        "type" -> Json.fromString(vuln._2))
      if (vuln._3.nonEmpty) {
        map += "vulnerabilities" -> Json.fromValues(vuln._3.map(vulnerabilityToJson))
      }
      JsonObject.fromMap(map).toJson

    }
    result match {
      case DependenciesWithVulnerabilities(vulnerabilitiesScanned) =>
        Json.fromValues(vulnerabilitiesScanned.map(tupleToJson))
      case DependenciesWithoutVulnerabilities(json) => json
    }

  }

  protected def checkDependencies(pathToScansibleJsonOutput: Path, shouldLookupVulnerabilities: Boolean): Either[String, DependenciesWithVulnerabilities] = {
    val dependenciesFileContent = os.read(pathToScansibleJsonOutput)
    val asJson = parse(dependenciesFileContent)
    asJson match {
      case Left(error) =>
        val errorMessage = s"ERROR: could not parse content at $pathToScansibleJsonOutput as JSON"
        Log.logError(errorMessage)
        Left(errorMessage)
      case Right(json) =>
        val jsonObject = json.asObject.get
        val moduleDependenciesJson = jsonObject.toMap.apply("module_dependencies")
        moduleDependenciesJson.asObject match {
          case None =>
            val errorMessage = s"ERROR: could not parse 'module_dependencies' of json $pathToScansibleJsonOutput as a dictionary"
            Log.logError(errorMessage)
            Left(errorMessage)
          case Some(moduleDependenciesObject) =>
            val dependencies: Iterable[VulnerabilitiesScanned] = moduleDependenciesObject.toMap.map((moduleDependency, arrayExpected) => {
              val jsonTuples: List[Json] = arrayExpected.asArray.get.toList
              val tuples: List[(String, String, List[Vulnerability])] = jsonTuples.map(tuple => {
                val tupleArray = tuple.asArray.get
                val name = tupleArray(0).asString.get
                val typ = tupleArray(1).asString.get
                val vulnerabilities = if (shouldLookupVulnerabilities) findVulnerabilities(name, typ) else Nil
                (name, typ, vulnerabilities)
              })
              tuples.foreach(tuple => printVulnerabilities(tuple._1, tuple._3))
              tuples
            })
            val flattenedDependencies = dependencies.flatten
            Right(DependenciesWithVulnerabilities(flattenedDependencies))
        }
    }
  }

  override def internalRun(): Either[String, DependenciesResult] = {
    val (scansibleOutput, pathToDependencies) = extractDependenciesWithScansible()
    if (!os.isFile(pathToDependencies)) {
      val errorMessage = s"ERROR: scansible could not extract dependencies to $pathToDependencies"
      Log.logError(errorMessage)
      Left(errorMessage)
    } else {
      if (!config.checkForVulnerabilities) {
        Log.logMessage(s"All discovered dependencies have been logged to '$pathToDependencies'")
        Log.logMessage("Rerun this command with the flag '--vulnerabilities' to check these dependencies for known vulnerabilities")
      }
      checkDependencies(pathToDependencies, config.checkForVulnerabilities)
//      val dependenciesFileContent = os.read(pathToDependencies)
//      val optJson = parse(dependenciesFileContent)
//      optJson match {
//        case Left(failure) => Left(failure._1)
//        case Right(json) => Right(DependenciesWithoutVulnerabilities(json))
//      }
    }
  }

}
