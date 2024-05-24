/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.eeittadminfrontend.services

import javax.inject.Inject
import org.apache.commons.io.IOUtils
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, UploadedForm, UploadedFormType }

import java.io.File
import java.util.zip.ZipFile
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import org.apache.pekko.stream.scaladsl._
import cats.effect.{ IO, Resource }
import org.apache.pekko.stream.Materializer
import uk.gov.hmrc.eeittadminfrontend.deployment.GithubPath

class BatchUploadService @Inject() (gformConnector: GformConnector)(implicit
  ec: ExecutionContext,
  materializer: Materializer
) {

  val processedTemplates: mutable.ArrayDeque[UploadedForm] = mutable.ArrayDeque()
  var done = true

  def uploadZip(file: File): Future[List[UploadedForm]] = {
    processedTemplates.clear()
    done = false

    val flowTemplates = Source(loadTemplatesFromZip(file)).mapAsync(2) { case (templateId, formData) =>
      processTemplate(templateId, formData)
    }

    val flowHandlebars = Source(loadHandlebarsTemplatesFromZip(file)).mapAsync(2) { case (templateId, formData) =>
      processHandlebarsTemplate(templateId, formData)
    }

    val flowHandlebarsSchema = Source(loadHandlebarsSchemasFromZip(file)).mapAsync(2) { case (templateId, formData) =>
      processHandlebarsSchema(templateId, formData)
    }

    //Run flow on background, some form may take up to 60s to load.
    (flowHandlebarsSchema ++ flowHandlebars ++ flowTemplates)
      .runWith(Sink.foreach { data =>
        processedTemplates += data
      })
      .onComplete(_ => done = true)

    Future(processedTemplates.toList)

  }

  private def loadFromZip(directory: String, file: File, fileExtension: String): List[(FormTemplateId, Array[Byte])] =
    Resource
      .fromAutoCloseable(IO(new ZipFile(file)))
      .use { zip =>
        IO(
          zip
            .entries()
            .asScala
            .filterNot(_.isDirectory)
            .filter(x => x.getName.endsWith(fileExtension))
            .filter(_.getName.startsWith(directory))
            .map { zipEntry =>
              val templateFile = Array.fill[Byte](zipEntry.getSize.toInt)(0)
              IOUtils.readFully(zip.getInputStream(zipEntry), templateFile)
              val formTemplateId =
                FormTemplateId(zipEntry.getName.replaceFirst(directory, "").replaceAll(fileExtension, ""))
              (formTemplateId, templateFile)
            }
            .toList
            .sortBy(_._1.value)
        )
      }
      .unsafeRunSync()

  private def loadTemplatesFromZip(file: File): List[(FormTemplateId, Array[Byte])] =
    loadFromZip(GithubPath.RootPath.zipPath, file, ".json")

  private def loadHandlebarsTemplatesFromZip(file: File): List[(FormTemplateId, Array[Byte])] =
    loadFromZip(GithubPath.HandlebarsPath.zipPath, file, ".hbs")

  private def loadHandlebarsSchemasFromZip(file: File): List[(FormTemplateId, Array[Byte])] =
    loadFromZip(GithubPath.HandlebarsSchemaPath.zipPath, file, ".json")

  private def processTemplate(templateId: FormTemplateId, template: Array[Byte]) = {
    val jsonTemplate = Json.parse(template)
    gformConnector
      .saveTemplate(templateId, jsonTemplate)
      .map { result =>
        result.fold(err => err, _ => "Ok")
      }
      .map { uploadResult =>
        UploadedForm(templateId, UploadedFormType.FormTemplate, uploadResult)
      }
      .recover { case e: Exception =>
        UploadedForm(templateId, UploadedFormType.FormTemplate, s"Unable to upload: ${e.getMessage}")
      }
  }

  private def processHandlebarsTemplate(templateId: FormTemplateId, template: Array[Byte]) =
    gformConnector
      .saveHandlebarsTemplate(templateId, new String(template, "UTF-8"))
      .map { result =>
        result.fold(err => err, _ => "Ok")
      }
      .map { uploadResult =>
        UploadedForm(templateId, UploadedFormType.Handlebars, uploadResult)
      }
      .recover { case e: Exception =>
        UploadedForm(templateId, UploadedFormType.Handlebars, s"Unable to upload: ${e.getMessage}")
      }

  private def processHandlebarsSchema(templateId: FormTemplateId, template: Array[Byte]) = {
    val jsonTemplate = Json.parse(template)
    gformConnector
      .saveHandlebarsSchema(templateId, jsonTemplate)
      .map { result =>
        result.fold(err => err, _ => "Ok")
      }
      .map { uploadResult =>
        UploadedForm(templateId, UploadedFormType.HandlebarsSchema, uploadResult)
      }
      .recover { case e: Exception =>
        UploadedForm(templateId, UploadedFormType.HandlebarsSchema, s"Unable to upload: ${e.getMessage}")
      }
  }

}
