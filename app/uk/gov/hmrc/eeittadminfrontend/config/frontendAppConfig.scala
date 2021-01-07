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

package uk.gov.hmrc.eeittadminfrontend.config

import play.api.Configuration

trait AppConfig {
  def appName: String
  def analyticsToken: String
  def analyticsHost: String
  def reportAProblemPartialUrl: String
  def reportAProblemNonJSUrl: String
  def optimizelyUrl: Option[String]
  def footerCookiesUrl: String
  def footerPrivacyPolicyUrl: String
  def footerTermsConditionsUrl: String
  def footerHelpUrl: String
}

class FrontendAppConfig(configuration: Configuration) extends AppConfig {

  private def loadConfig(key: String) =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private val contactHost = configuration.getOptional[String](s"contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "MyService"

  override lazy val appName = loadConfig("appName")
  override lazy val analyticsToken = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"google-analytics.host")
  override lazy val reportAProblemPartialUrl =
    s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl =
    s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override def optimizelyUrl =
    for {
      url       <- configuration.getOptional[String]("optimizely.url")
      projectId <- configuration.getOptional[String]("optimizely.projectId")
    } yield s"$url$projectId.js"

  override lazy val footerCookiesUrl = loadConfig(s"footer-cookies-url")
  override lazy val footerPrivacyPolicyUrl = loadConfig(s"footer-privacy-policy-url")
  override lazy val footerTermsConditionsUrl = loadConfig(s"footer-terms-conditions-url")
  override lazy val footerHelpUrl = loadConfig(s"footer-help-url")
}
