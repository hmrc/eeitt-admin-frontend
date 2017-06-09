package uk.gov.hmrc.eeittadminfrontend.controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.eeittadminfrontend.connectors.{EnrollmentKey, KeyValuePair, KnownFacts, Verifiers}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

class BulkGGLoad(val authConnector: AuthConnector)(implicit val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  val knownFactsForm = Form(
    mapping(
      "enrollmentKey" -> mapping(
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


}
