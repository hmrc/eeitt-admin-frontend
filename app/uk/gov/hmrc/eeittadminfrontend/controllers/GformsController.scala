/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.controllers

import java.util.Base64

import org.apache.commons.codec
import com.google.common.io.BaseEncoding
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTypeId, GformId }
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future
import scala.io.Source

class GformsController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def getGformByFormType = Authentication.async { implicit request =>
    gFormForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
      },
      gformIdAndVersion => {
        Logger.info(s" ${request.session.get("token").get} Queried for ${gformIdAndVersion.formTypeId}")
        GformConnector.getGformsTemplate(gformIdAndVersion.formTypeId).map { x => Ok(Json.prettyPrint(x))
        }
      }
    )
  }

  val vat126Template: JsValue = Json.parse("{\n  \"_id\": \"HO10\",\n  \"formName\": \"Hydrocarbon oils\",\n  \"description\": \"\",\n  \"dmsSubmission\": {\n    \"customerId\": \"${auth.payenino}\",\n    \"classificationType\": \"BT-NRU-Environmental\",\n    \"businessArea\": \"FinanceOpsCorpT\"\n  },\n  \"authConfig\":{\n    \"authModule\": \"legacyEEITTAuth\",\n    \"regimeId\": \"GF\"\n  },\n  \"submitSuccessUrl\": \"http://www.google.co.uk\",\n  \"submitErrorUrl\": \"http://www.yahoo.co.uk\",\n  \"sections\": [\n    {\n      \"title\": \"Enter your business details\",\n      \"shortName\": \"Business details\",\n      \"fields\": [\n        {\n          \"id\": \"business-name\",\n          \"type\": \"text\",\n          \"label\": \"Business name\",\n          \"mandatory\": \"true\"\n        },\n        {\n          \"id\": \"telephone-number\",\n          \"type\": \"text\",\n          \"label\": \"Phone number\",\n          \"format\": \"telephoneNumber\"\n        },\n        {\n          \"id\": \"business-address\",\n          \"label\": \"Registered business address\",\n          \"mandatory\": \"true\",\n          \"type\": \"address\",\n          \"international\": \"yes\"\n        }\n      ]\n    },\n    {\n      \"title\": \"Enter the start and end dates for this return\",\n      \"shortName\": \"Start and end dates for this return\",\n      \"fields\": [\n        {\n          \"id\": \"start-date\",\n          \"type\": \"date\",\n          \"label\": \"Start date\",\n          \"mandatory\": \"true\",\n          \"helpText\": \"Example: 10 07 2017\"\n        },\n        {\n          \"id\": \"end-date\",\n          \"type\": \"date\",\n          \"label\": \"End date\",\n          \"mandatory\": \"true\",\n          \"helpText\": \"Example: 10 10 2017\"\n        }\n      ]\n    },\n    {\n      \"title\": \"Upload your return document\",\n      \"fields\": [\n        {\n          \"type\": \"file\",\n          \"id\": \"spreadsheet\",\n          \"label\": \"\"\n        },\n        {\n          \"id\": \"upload-info\",\n          \"type\": \"info\",\n          \"infoText\": \"The document you upload must be:  \\n\\n* less that 5 megabytes (5MB)  \\n* in PDF or JPEG format\",\n          \"label\": \"\"\n        },\n        {\n          \"id\": \"upload-contents-info\",\n          \"type\": \"info\",\n          \"infoType\": \"long\",\n          \"infoText\": \"Content goes here...\",\n          \"label\": \"What your document must contain\"\n        }\n      ]\n    },\n    {\n      \"title\": \"Enter the total amount of your claim\",\n      \"fields\": [\n        {\n          \"id\": \"total-claim\",\n          \"type\": \"text\",\n          \"label\": \"\",\n          \"mandatory\": \"true\",\n          \"format\": \"sterling\"\n        }\n      ]\n    },\n    {\n      \"title\": \"Enter your contact person's details\",\n      \"description\": \"This is the person we will contact if we need to discuss your return.\",\n      \"fields\": [\n        {\n          \"id\": \"name\",\n          \"type\": \"text\",\n          \"label\": \"Full name\",\n          \"mandatory\": \"true\"\n        },\n        {\n          \"id\": \"email\",\n          \"type\": \"text\",\n          \"label\": \"Email address\",\n          \"mandatory\": \"true\",\n          \"format\": \"email\"\n        }\n      ]\n    }\n  ],\n  \"declarationSection\": {\n    \"shortName\": \"Sign and submit your return\",\n    \"title\": \"Sign and submit your return\",\n    \"fields\": [\n      {\n        \"id\": \"declarationNameGroup\",\n        \"type\": \"group\",\n        \"label\": \"Approver's details\",\n        \"helpText\": \"Tell us who is approving this return.\",\n        \"fields\": [\n          {\n            \"id\": \"declarationFullName\",\n            \"type\": \"text\",\n            \"label\": \"Full name\"\n          },\n          {\n            \"id\": \"declarationJobTitle\",\n            \"type\": \"text\",\n            \"label\": \"Job title\",\n            \"helpText\": \"Example: Finance Director\"\n          }\n        ]\n      },\n      {\n        \"id\": \"declarationGroup\",\n        \"type\": \"group\",\n        \"label\": \"Declaration\",\n        \"fields\": [\n          {\n            \"id\": \"declarationText\",\n            \"type\": \"info\",\n            \"label\": \"\",\n            \"infoType\": \"noformat\",\n            \"infoText\": \"By submitting the return, you confirm that the details provided are true and complete.\"\n          }\n        ]\n      }\n    ]\n  },\n  \"acknowledgementSection\": {\n    \"shortName\": \"Acknowledgement Page\",\n    \"title\": \"Acknowledgement Page\",\n    \"fields\": [\n      {\n        \"id\": \"ackPageInfoTop\",\n        \"type\": \"info\",\n        \"format\": \"noformat\",\n        \"label\": \"If you owe duty\",\n        \"infoText\": \"## If you owe duty\\n If you have a Direct Debit set up for this tax, you don't need to do anything. Your payment will be collected automatically.  \\n\\nOtherwise, paying electronically is an easy and secure way to pay. Use the details below and make sure you include your registration number (XZGR 00000 123456) on any payment.  \\n\\nSort code: 08-32-00  \\nAccount number: 12000903  \\nAccount name: HMRC Miscellaneous\"\n      },\n      {\n        \"id\": \"ackPageWarning\",\n        \"type\": \"info\",\n        \"format\": \"important\",\n        \"label\": \"\",\n        \"infoText\": \"Make sure you pay the duty owed or you may face a penalty.\"\n      },\n      {\n        \"id\": \"ackPageInfoBottom\",\n        \"type\": \"info\",\n        \"format\": \"noformat\",\n        \"label\": \"If you need to contact us about your return\",\n        \"infoText\": \"## If you need to contact us about your return\\n Call [Content TBC] or email [TBC], quoting your registration number XZGR 00000 123456.\"\n      }\n    ]\n  }\n}")

  def saveGformSchema = Authentication.async(parse.multipartFormData) { implicit request =>
    val template = Json.parse(org.apache.commons.codec.binary.Base64.decodeBase64(request.body.dataParts("template").mkString).map(_.toChar).mkString)
    GformConnector.saveTemplate(template).map {
      x =>
        {
          Logger.info(s" ${request.session.get("token").get} saved ID: ${template \ "_id"} }")
          Ok("Saved")
        }
    }
  }

  def getAllTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form templates")
    GformConnector.getAllGformsTemplates.map(x => Ok(x))
  }

  def getAllSchema = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form Schema")
    GformConnector.getAllSchema.map(x => Ok(x))
  }

  def deleteGformTemplate = Authentication.async { implicit request =>
    gFormForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
      },
      gformId => {
        Logger.info(s" ${request.session.get("token").get} deleted ${gformId.formTypeId} ")
        GformConnector.deleteTemplate(gformId.formTypeId).map(res => Ok)
      }
    )
  }

  def gformPage = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
  }

  def gformAuthor = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.author_tool()))
  }

  val gFormForm: Form[GformId] = Form(
    mapping(
      "formTypeId" -> mapping(
        "value" -> text
      )(FormTypeId.apply)(FormTypeId.unapply)
    )(GformId.apply)(GformId.unapply)
  )
}

