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

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.internalauth.client.{ FrontendAuthComponents, Retrieval }
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.internalauth.client.{ IAAction, Predicate, Resource, ResourceLocation, ResourceType }

abstract class GformAdminFrontendController(
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents
) extends FrontendController(messagesControllerComponents) {

  private val readAction = IAAction("READ")
  private val writeAction = IAAction("WRITE")
  private val deleteAction = IAAction("DELETE")
  def authorizedRead = authAction("*", readAction)
  def authorizedWrite = authAction("*", writeAction)
  def authorizedDelete = authAction("*", deleteAction)

  private def authAction =
    (location: String, action: IAAction) =>
      frontendAuthComponents
        .authorizedAction(
          continueUrl = routes.AuthController.login,
          predicate = Predicate.Permission(
            Resource(ResourceType("eeitt-admin-frontend"), ResourceLocation(location)),
            action
          ),
          retrieval = Retrieval.username
        )
}
