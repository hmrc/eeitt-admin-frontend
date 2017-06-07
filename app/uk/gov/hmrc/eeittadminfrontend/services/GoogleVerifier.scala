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

package uk.gov.hmrc.eeittadminfrontend.services

import java.util.Collections

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import play.api.Logger
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.ClientID

object GoogleVerifier {

  val clientID: ClientID = pureconfig.loadConfigOrThrow[ClientID]("clientid")

  lazy val tokenVerifier: GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance)
    .setAudience(Collections.singletonList(clientID.id))
    .build()

  def apply(token: String): String = {
    tokenVerifier.verify(token).getPayload.getEmail
  }
}