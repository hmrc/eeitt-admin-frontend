/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend

import akka.actor.ActorSystem
import play.api.{ Configuration, Play }

object InjectionDodge {
  implicit def current = Play.current
  def actorSystem: ActorSystem = current.actorSystem
  def configuration: Configuration = current.configuration
  def mode = current.mode
  def appNameConfiguration: Configuration = current.configuration
  val runModeConfiguration = current.configuration
}
