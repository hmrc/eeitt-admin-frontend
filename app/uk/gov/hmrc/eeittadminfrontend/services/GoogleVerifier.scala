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

import java.net.{Authenticator, InetSocketAddress, PasswordAuthentication, Proxy, SocketAddress}
import java.util.Collections

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport.Builder
import com.google.api.client.json.jackson2.JacksonFactory
import org.apache.http.Header
import org.apache.http.impl.DefaultHttpRequestFactory
import play.api.Logger
import play.api.libs.json.Format
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.ClientID

class GoogleVerifier extends GoogleVerifierHelper

case class Squid(environmentEnabled: Boolean, username: String, password: String, host: String, port: Int)

trait GoogleVerifierHelper {

  val clientID: ClientID = pureconfig.loadConfigOrThrow[ClientID]("clientid")

  //  val environmentProxy: Boolean = pureconfig.loadConfigOrThrow[Boolean]("proxy.proxyRequiredForThisEnvironment")
  val squid: Squid = pureconfig.loadConfigOrThrow[Squid]("proxy")

  val httpTransport: NetHttpTransport = if (squid.environmentEnabled) {
    val authenticator = new Authenticator() {

      override def getPasswordAuthentication(): PasswordAuthentication = {
        new PasswordAuthentication(squid.username, squid.password.toCharArray)
      }
    }
    Authenticator.setDefault(authenticator)
    System.setProperty("http.proxyHost", squid.host)
    System.setProperty("http.proxyPort", squid.port.toString)
    val proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(squid.host, squid.port))
    new NetHttpTransport.Builder().setProxy(proxy).doNotValidateCertificate().build()
  } else GoogleNetHttpTransport.newTrustedTransport()

  lazy val tokenVerifier: GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(httpTransport, JacksonFactory.getDefaultInstance)
    .setAudience(Collections.singletonList(clientID.id))
    .build()

  def apply(token: String): String = {
    tokenVerifier.verify(token).getPayload.getEmail
  }
}