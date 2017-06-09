package uk.gov.hmrc.eeittadminfrontend.connectors

import play.api.libs.json.JsValue
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.play.http.{HttpPost, HttpPut}

class EMACConnector extends EMACConnector {


}

case class KeyValuePair(key: String, value: String)
case class Verifiers(verifiers : List[KeyValuePair])
case class EnrollmentKey(value: String)
case class KnownFacts(enrollmentKey: EnrollmentKey, verifiers: Verifiers)
case class Enrollment(userid: String, enrollmentKey: EnrollmentKey)

trait EMACConnectorHelper {

  val PUT : HttpPut = WSHttp
  val POST : HttpPost = WSHttp

  def loadKF(knownFacts: KnownFacts) = {
    PUT.PUT[Verifiers, JsValue](s"http://enrolment-store-proxy.protected.mdtp:80/enrolment-store-proxy/enrolment-store/enrolments/${knownFacts.enrollmentKey}", knownFacts.verifiers)
  }

  def assignEnrollment(enrollment: Enrollment) = {
    POST.POSTEmpty(s"http://enrolment-store-proxy.protected.mdtp:80/enrolment-store-proxy/enrolment-store/users/${enrollment.userid}/enrolments/${enrollment.enrollmentKey.value}")
  }

}