package uk.gov.hmrc.eeittadminfrontend

import java.io.File

import org.scalatest.TestSuite
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory}
import play.api.{Configuration, Environment, Mode}

trait ApplicationComponentsOnePerSuite extends BaseOneAppPerSuite with FakeApplicationFactory {
  this: TestSuite =>

  def additionalConfiguration: Map[String, Any] = Map.empty[String, Any]

  private lazy val config = Configuration.from(additionalConfiguration)

  override lazy val fakeApplication =
    new ApplicationLoader().load(context.copy(initialConfiguration = context.initialConfiguration ++ config))

  def context: play.api.ApplicationLoader.Context = {
    val classLoader = play.api.ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new File("."), classLoader, Mode.Test)
    play.api.ApplicationLoader.createContext(env)
  }
}

