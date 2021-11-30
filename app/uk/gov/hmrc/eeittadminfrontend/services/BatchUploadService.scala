/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.stream.Materializer
import org.apache.commons.io.IOUtils
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId

import java.io.File
import java.util.zip.ZipFile
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import collection.JavaConverters._
import akka.stream.scaladsl._
import cats.effect.{ IO, Resource }

class BatchUploadService(gformConnector: GformConnector)(implicit ec: ExecutionContext, materializer: Materializer) {

  val processedTemplates: mutable.MutableList[(FormTemplateId, String)] = mutable.MutableList()
  var done = true

  def uploadZip(file: File): Future[List[(FormTemplateId, String)]] = {
    processedTemplates.clear()
    done = false

    val flow = Source(loadTeplatesFromZip(file)).mapAsyncUnordered(2) { case (templateId, formData) =>
      processTemplate(templateId, formData)
    }

    //Run flow on background, some form may take up to 60s to load.
    flow
      .runWith(Sink.foreach { data =>
        processedTemplates += data
      })
      .onComplete(_ => done = true)

    Future(processedTemplates.toList)

  }

  def loadTeplatesFromZip(file: File): List[(FormTemplateId, Array[Byte])] =
    Resource
      .fromAutoCloseable(IO(new ZipFile(file)))
      .use { zip =>
        IO(
          zip
            .entries()
            .asScala
            .filterNot(_.isDirectory)
            .filter(x => x.getName.endsWith(".json"))
            .map { zipEntry =>
              val templateFile = Array.fill[Byte](zipEntry.getSize.toInt)(0)
              IOUtils.readFully(zip.getInputStream(zipEntry), templateFile)
              val formTemplateId = FormTemplateId(zipEntry.getName.replaceAll("\\.json", ""))
              (formTemplateId, templateFile)
            }
            .toList
            .sortBy(_._1.value)
            .reverse
        )
      }
      .unsafeRunSync()

  def processTemplate(templateId: FormTemplateId, template: Array[Byte]) = {
    val jsonTemplate = Json.parse(template)
    gformConnector
      .saveTemplate(templateId, jsonTemplate)
      .map { result =>
        result.fold(err => err, _ => "Ok")
      }
      .map { uploadResult =>
        (templateId, uploadResult)
      }
      .recover { case e: Exception =>
        (templateId, s"Unable to upload: ${e.getMessage}")
      }
  }

}
