/*
 * Copyright 2025 HM Revenue & Customs
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

import com.microsoft.playwright._
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject

class ScreenshotController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents
) extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {
  def test = authorizedRead { request =>
    val playwright: Playwright = Playwright.create()
    val browser: Browser = playwright.chromium().launch()
    val context: BrowserContext = browser.newContext()
    val page: Page = context.newPage()
    val authUrl = "https://www.qa.tax.service.gov.uk/auth-login-stub/gg-sign-in"
    val screenshotUrl =
      "https://www.qa.tax.service.gov.uk/submissions/form/give-additional-information-for-creative-industry-tax-relief-or-credit/automatic-capture?n=4,8,n9&se=t&ff=t"
    val response: Response = page.navigate(
      authUrl
    )
    page.navigate(screenshotUrl)
    val screenshot = page.screenshot(
      new Page.ScreenshotOptions().setFullPage(true)
    )
    response.finished()
    playwright.close()
    Ok(screenshot).as("image/png")
  }
}
