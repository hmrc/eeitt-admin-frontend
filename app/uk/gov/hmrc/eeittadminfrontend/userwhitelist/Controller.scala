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

package uk.gov.hmrc.eeittadminfrontend.userwhitelist

import play.api.i18n.{ I18nSupport, MessagesApi }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class Controller(val authConnector: AuthConnector, repository: Repository)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def displayPage = Authentication.async { implicit request =>
    repository.findAll().map { users =>
      Ok(uk.gov.hmrc.eeittadminfrontend.userwhitelist.views.html.form(users))
    }
  }

  def process = Authentication.async { implicit request =>
    request.body.asFormUrlEncoded match {
      case None => Future.successful(BadRequest)
      case Some(fieldMap) =>
        fieldMap.get("process") match {
          case Some(ArrayBuffer("addUsers")) =>
            fieldMap.get("userList") match {
              case Some(Seq(stringWithUsers)) =>
                val userList = stringWithUsers.split("""[\s,]""").toSeq
                repository.bulkInsert(userList.map(WhitelistUser.apply)).map { cosa =>
                  Redirect(uk.gov.hmrc.eeittadminfrontend.userwhitelist.routes.Controller.displayPage().url)
                }
            }

          //        case Some("removeUsers" :: Nil) =>

          case _ => Future.successful(BadRequest)
        }
        println("HOLA " + fieldMap)

    }
    Future.successful(Ok)
  }
}
