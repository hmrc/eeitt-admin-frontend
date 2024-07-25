package uk.gov.hmrc.eeittadminfrontend.models

import org.scalatest.matchers.must.Matchers.{ contain, convertToAnyMustWrapper }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes.SubmissionRef
import uk.gov.hmrc.eeittadminfrontend.utils.FileUtil

class DmsReportSpec extends AnyWordSpecLike with TableDrivenPropertyChecks {

  "DmsReport.fromFile" should {
    "return a DmsReport object" in {
      val content =
        """FCA-C1VD-R6W,30d1c5ca-198b-45a2-bc16-083768fdff63,
          |EO9-OPY9-53S,9b6c343a-9b3c-4600-ae9d-b697d1649259,
          |UEQ-DG7P-RPQ,e0fcc55f-28e9-4ca9-9e34-d5ba49c5796b,
          |2DJ-XAH7-3AW,da15f8d6-41c8-4e88-a013-b100da1ed90a,
          |X0T-CIB4-OFS,e4f94466-d1b4-4831-a09b-06512d5fc4f9,
          |R4E-IMYV-4CJ,8330cbb3-75aa-465d-9cbc-fbb8f7070b85,""".stripMargin

      val dmsReport = DmsReport(FileUtil.createFile("dmsReport.csv", content))

      assert(dmsReport.isInstanceOf[DmsReport])

      dmsReport.data must contain theSameElementsAs List(
        DmsReportData(SubmissionRef("FCA-C1VD-R6W"), EnvelopeId("30d1c5ca-198b-45a2-bc16-083768fdff63")),
        DmsReportData(SubmissionRef("EO9-OPY9-53S"), EnvelopeId("9b6c343a-9b3c-4600-ae9d-b697d1649259")),
        DmsReportData(SubmissionRef("UEQ-DG7P-RPQ"), EnvelopeId("e0fcc55f-28e9-4ca9-9e34-d5ba49c5796b")),
        DmsReportData(SubmissionRef("2DJ-XAH7-3AW"), EnvelopeId("da15f8d6-41c8-4e88-a013-b100da1ed90a")),
        DmsReportData(SubmissionRef("X0T-CIB4-OFS"), EnvelopeId("e4f94466-d1b4-4831-a09b-06512d5fc4f9")),
        DmsReportData(SubmissionRef("R4E-IMYV-4CJ"), EnvelopeId("8330cbb3-75aa-465d-9cbc-fbb8f7070b85"))
      )
      dmsReport.count mustBe 6
      dmsReport.data.length mustBe 6
    }

    "throw an IllegalArgumentException" when {
      val scenarios = Table(
        ("content", "csvIssue"),
        (
          """FCA-C1VD-R6W,30d1c5ca-198b-45a2-bc16-083768fdff63,xyz,
            |EO9-OPY9-53S,9b6c343a-9b3c-4600-ae9d-b697d1649259,""".stripMargin,
          "there is a additional field in the file"
        ),
        (
          """FCA-C1VD-R6W,
            |EO9-OPY9-53S,9b6c343a-9b3c-4600-ae9d-b697d1649259,""".stripMargin,
          "there is missing field in the file"
        )
      )

      forAll(scenarios) { (content: String, csvIssue: String) =>
        s"$csvIssue" in {
          assertThrows[IllegalArgumentException] {
            DmsReport(FileUtil.createFile("dmsReport.csv", content))
          }
        }
      }
    }
  }
}
