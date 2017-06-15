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

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class BulkGGLoad(val authConnector: AuthConnector, eMACConnector: EMACConnector)(implicit val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  val knownFactsForm = Form(
    mapping(
      "enrollmentKey" -> mapping(
        "service" -> nonEmptyText,
        "identifier" -> nonEmptyText,
        "value" -> nonEmptyText
      )(EnrollmentKey.apply)(EnrollmentKey.unapply),
      "verifiers" -> mapping(
        "verifiers" -> list(mapping(
          "key" -> nonEmptyText,
          "value" -> nonEmptyText
        )(KeyValuePair.apply)(KeyValuePair.unapply))
      )(Verifiers.apply)(Verifiers.unapply)
    )(KnownFacts.apply)(KnownFacts.unapply)
  )

  val assignEnrollmentForm = Form(
    mapping(
      "groupId" -> nonEmptyText,
      "enrolmentKey" -> mapping(
        "service" -> nonEmptyText,
        "identifier" -> nonEmptyText,
        "value" -> nonEmptyText
      )(EnrollmentKey.apply)(EnrollmentKey.unapply),
      "verfifiers" -> mapping(
        "verifier" -> list(mapping(
          "key" -> nonEmptyText,
          "value" -> nonEmptyText
        )(KeyValuePair.apply)(KeyValuePair.unapply))
      )(Verifiers.apply)(Verifiers.unapply),
      "credId" -> nonEmptyText
    )(Enrollment.apply)(Enrollment.unapply)
  )

  def loadKF(): Action[AnyContent] = Authentication.async { implicit request =>
    knownFactsForm.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest("Failed")),
      success =>
        Future.successful(Ok("Success"))
    )
  }

  def assignEnrollment(): Action[AnyContent] = Authentication.async { implicit request =>
    assignEnrollmentForm.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest("Failed")),
      success =>
        Future.successful(Ok("Ok"))
    )
  }

}
