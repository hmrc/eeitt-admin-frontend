package uk.gov.hmrc.eeittadminfrontend.models

import play.api.Logger
import play.api.libs.json._

trait UserType

object UserType {

  implicit val format: Format[UserType] = new Format[UserType] {
    override def reads(json: JsValue): JsResult[UserType] = {
      (json \ "user").getOrElse(JsString("Error")) match {
        case JsString("Agent") => JsSuccess(Agent)
        case JsString("Business") => JsSuccess(Business)
        case _ => JsError("Bob")
      }
    }

    override def writes(o: UserType): JsValue = {
      o match {
        case Agent => Json.obj("user" -> "Agent")
        case Business => Json.obj("user" -> "Business")
        case _ =>
          Logger.error("illegal arguement")
          JsString("Error")
      }
    }
  }
}

object Agent extends UserType
object Business extends UserType