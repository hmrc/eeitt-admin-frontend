package uk.gov.hmrc.eeittadminfrontend

import play.api.mvc.AnyContentAsFormUrlEncoded
import uk.gov.hmrc.eeittadminfrontend.controllers.AuthController

object TestUsers {

  def invalidUser() = {
    AnyContentAsFormUrlEncoded(Map("email" -> Seq("invalid@test.com")))
  }

  def registration() = {
    AnyContentAsFormUrlEncoded(Map("email.value" -> Seq("test@test.com"), "permissions.[0].value" -> Seq("Query"), "permissions.[1].value" -> Seq("Deltas")))
  }

  def validUser() = {
    AnyContentAsFormUrlEncoded(Map("email" -> Seq("test@test.com")))
  }

}
