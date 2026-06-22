package controllers

import better.files

import java.nio.file.{Files, Path}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import config.StartupConfiguration
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{FileIO, Sink}
import org.apache.pekko.util.ByteString
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.core.parsers.Multipart.FileInfo
import util.Log

import java.io.File
import java.util.zip.ZipEntry

abstract class BaseInfraMapperController(cc:MessagesControllerComponents)
                                        (implicit executionContext: ExecutionContext)
  extends MessagesAbstractController(cc) {

  protected def start(startupConfiguration: StartupConfiguration): Either[String, Unit]

//  protected def removeTempFiles(config: StartupConfiguration): Unit = {
//      def removeOptFile(path: String): Unit = {
//        try {
//          Files.deleteIfExists(Path.of(path))
//        } catch {
//          case _: Throwable =>
//        }
//      }
//      config.inputManifestPath.foreach(removeOptFile)
//    }

  protected def extractZippedProject(optInputProject: File): String = {
    val zipped: files.File = better.files.File(optInputProject.toPath)
    val unzippedFolder: files.File = zipped.unzip((z: ZipEntry) => {
      !z.getName.startsWith("__MACOSX")
    })
    val unzippedFolderPath = unzippedFolder.path.toString
    unzippedFolderPath
  }

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than
   * using Play's TemporaryFile class.  Deletion must happen explicitly on
   * completion, rather than TemporaryFile (which uses finalization to
   * delete temporary files).
   *
   * @return
   */
  protected def handleFilePartAsFile(fileInfo: FileInfo): Accumulator[ByteString, FilePart[File]] = fileInfo match {
    case FileInfo(partName, filename, contentType, _) =>
      val splitOnDot = filename.split('.')
      val extension = if (splitOnDot.length >= 2) {
        // If file has an extension, give the complete file the same extension
        val extension = splitOnDot.last
        s".$extension"
      } else {
        // File does not have an extension, use empty extension
        ""
      }
      val path: Path = java.nio.file.Files.createTempFile(filename, extension)
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          Log.logMessage(s"count = $count, status = $status")
          FilePart(partName, filename, contentType, path.toFile)
      }
  }

  protected def runFromStartupConfig(optStartupConfig: Option[StartupConfiguration]): Result = {
    optStartupConfig match {
      case None =>
        BadRequest
      case Some(startupConfig) =>
        println(s"startupConfig = $startupConfig")
        val maybeResult = start(startupConfig)
        //        removeTempFiles(startupConfig)
        maybeResult match {
          case Left(errorMessage) =>
            InternalServerError(errorMessage)
          case Right(_) =>
            Ok(s"Operation successful")
        }
    }
  }

}
