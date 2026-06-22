package controllers

import app.Main
import config._
import play.api.*
import play.api.data.Form
import play.api.data.Forms.*
import play.api.mvc.*
import play.api.mvc.MultipartFormData.FilePart
import play.core.parsers.Multipart.FileInfo

import java.io.File
import javax.inject.*
import scala.concurrent.ExecutionContext

// Contains the fields of the request for scanning an Ansible file for weaknesses
case class FindAnsibleDependenciesForm(zippedAnsibleProject: Option[File], lookupVulnerabilities: Boolean)
object FindAnsibleDependenciesForm {
  def unapply(formData: FindAnsibleDependenciesForm): Option[(Option[File], Boolean)] = {
    Some(formData.zippedAnsibleProject, formData.lookupVulnerabilities)
  }
}

@Singleton
class DependenciesController @Inject()(cc:MessagesControllerComponents)
                              (implicit executionContext: ExecutionContext)
  extends BaseInfraMapperController(cc) {

  val form = Form(
    mapping(
      "zippedAnsibleProject" -> ignored(Option.empty[java.io.File]),
      "lookupVulnerabilities" -> boolean
    )(FindAnsibleDependenciesForm.apply)(FindAnsibleDependenciesForm.unapply)
  )

  def toStartupConfiguration(optZippedAnsibleFiles: Option[File], form: FindAnsibleDependenciesForm): Option[StartupConfiguration] = optZippedAnsibleFiles match {
    case None => None
    case Some(zippedAnsibleFiles) =>
      val unzippedFolderPath: String = extractZippedProject(zippedAnsibleFiles)
      Some(StartupConfiguration(FetchDependenciesCommand, unzippedFolderPath, FetchDependenciesOptions(form.lookupVulnerabilities)))
  }

  def find_dependencies: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.find_dependencies(form))
  }

  protected def start(config: StartupConfiguration): Either[String, Unit] = {
    try {
      Main.run(config)
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        Right(e.getMessage)
    }
  }

  def upload: Action[MultipartFormData[File]] = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    val optAnsibleFile: Option[File] = request.body.file("ansibleFile").map {
      case FilePart(key, filename, contentType, file, fileSize, dispositionType, _) =>
        println(s"filename = $filename")
        file
    }
    val optFormData: Option[FindAnsibleDependenciesForm] = form.bindFromRequest(request.body.dataParts).bindFromRequest().fold(
      errors => {
        println(s"errors = $errors"); None
      }, x => Some(x))
    optFormData match {
      case None => BadRequest
      case Some(formData) =>
        val optStartupConfig = toStartupConfiguration(optAnsibleFile, formData)
        runFromStartupConfig(optStartupConfig)
    }
  }
}
