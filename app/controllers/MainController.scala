package controllers

import better.files
import config.{AnalyseWeaknessesCommand, AnalyseWeaknessesOptions, StartupConfiguration}

import java.io.File
import javax.inject.*
import play.api.*
import play.api.data.Form
import play.api.data.Forms.*
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.*
import play.core.parsers.Multipart.FileInfo

import java.util.zip.ZipEntry
import scala.concurrent.ExecutionContext

import app.Main
import util.Log

// Contains the fields of the request for scanning an Ansible file for weaknesses
case class ScanAnsibleWeaknessesForm(ansibleFile: Option[File])
object ScanAnsibleWeaknessesForm {
  def unapply(formData: ScanAnsibleWeaknessesForm): Option[(Option[File])] = {
    Some(formData.ansibleFile)
  }
}

@Singleton
class MainController @Inject()(cc:MessagesControllerComponents)
                              (implicit executionContext: ExecutionContext)
  extends BaseInfraMapperController(cc) {

  val form = Form(
    mapping(
      "ansibleFile" -> ignored(Option.empty[java.io.File]),
    )(ScanAnsibleWeaknessesForm.apply)(ScanAnsibleWeaknessesForm.unapply)
  )

  def toStartupConfiguration(optAnsibleFile: Option[File]): Option[StartupConfiguration] = optAnsibleFile match {
    case None => None
    case Some(ansibleFile) =>
      Some(StartupConfiguration(AnalyseWeaknessesCommand, ansibleFile.getAbsolutePath, AnalyseWeaknessesOptions()))
  }

  def index: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.index(form))
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
    val optStartupConfig = toStartupConfiguration(optAnsibleFile)
    runFromStartupConfig(optStartupConfig)
  }
}
