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

package uk.gov.hmrc.eeittadminfrontend.controllers

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

class AuthController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents
) extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def login: Action[AnyContent] =
    authorizedRead { request =>
      val username = request.retrieval.value
      logger.info(s"User '$username' logged in")
      Redirect(routes.GformsController.gformPage)
    }
}
