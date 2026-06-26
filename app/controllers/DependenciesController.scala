package controllers

import config.*
import play.api.*
import play.api.data.Form
import play.api.data.Forms.*
import play.api.mvc.*
import play.api.mvc.MultipartFormData.FilePart
import play.core.parsers.Multipart.FileInfo
import util.removeOptFile

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
      removeOptFile(zippedAnsibleFiles.getAbsolutePath)
      Some(StartupConfiguration(FetchDependenciesCommand, unzippedFolderPath, FetchDependenciesOptions(form.lookupVulnerabilities)))
  }

  def find_dependencies: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.find_dependencies(form))
  }

  def upload: Action[MultipartFormData[File]] = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    val optZippedAnsibleProject: Option[File] = request.body.file("zippedAnsibleProject").map {
      case FilePart(key, filename, contentType, file, fileSize, dispositionType, _) =>
        file
    }
    val optFormData: Option[FindAnsibleDependenciesForm] = form.bindFromRequest(request.body.dataParts).bindFromRequest().fold(
      errors => { None }, x => Some(x))
    optFormData match {
      case None => BadRequest
      case Some(formData) =>
        val newFormData = formData.copy(zippedAnsibleProject = optZippedAnsibleProject)
        val optStartupConfig = toStartupConfiguration(optZippedAnsibleProject, newFormData)
        runFromStartupConfig(optStartupConfig)
    }
  }
}
