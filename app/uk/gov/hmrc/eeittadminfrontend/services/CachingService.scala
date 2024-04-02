/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.{ EitherT, NonEmptyList }
import cats.effect.IO
import cats.syntax.all._
import github4s.domain.{ Commit, Content }
import java.util.concurrent.atomic.{ AtomicInteger }
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, CommitSha, Filename, GithubContent, GithubPath }
import scala.collection.concurrent.TrieMap

sealed trait CacheStatus

object CacheStatus {
  case object Uninitialized extends CacheStatus
  case object Failed extends CacheStatus
  case class Refreshed(commit: Commit) extends CacheStatus
  case class RefreshInProgress(progress: Int, total: Int, commit: Option[Commit]) extends CacheStatus
}

object RefreshCache {
  case class Progress(progress: Int, total: Int, commit: Option[Commit])
}

class CachingService @Inject() (githubService: GithubService) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val numberOfTemplates: AtomicInteger = new AtomicInteger(0)

  private val cache: TrieMap[Filename, GithubContent] = new TrieMap()

  private var refreshCommit: Option[Commit] = None

  def githubContents: List[(Filename, GithubContent)] = cache.toList

  def githubContent(filename: Filename): Option[GithubContent] = cache.get(filename)

  def cacheStatus: CacheStatus =
    if (numberOfTemplates.get == 0)
      CacheStatus.Uninitialized
    else if (cache.size == numberOfTemplates.get)
      refreshCommit.fold[CacheStatus](CacheStatus.Failed)(commit => CacheStatus.Refreshed(commit))
    else
      CacheStatus.RefreshInProgress(cache.size, numberOfTemplates.get, refreshCommit)

  private def retrieveTemplates(
    lastCommit: Commit,
    countDownLatch: CountDownLatch,
    githubTemplates: NonEmptyList[Content]
  ): EitherT[IO, String, NonEmptyList[Option[GithubContent]]] = {
    refreshCommit = Some(lastCommit)
    cache.clear()
    numberOfTemplates.set(githubTemplates.size)
    countDownLatch.countDown()
    githubTemplates.parTraverse { githubTemplate =>
      val filename = Filename(githubTemplate.name)
      val githubPath = GithubPath(githubTemplate.path)
      val blobSha = BlobSha(githubTemplate.sha)
      (
        githubService.retrieveFormTemplate(filename, blobSha, githubPath),
        githubService.getCommitByPath(githubPath)
      ).parMapN { case ((formTemplateId, contentValue), commit) =>
        val githubContent = GithubContent(
          formTemplateId,
          contentValue,
          blobSha,
          CommitSha(commit.sha),
          githubPath
        )
        logger.info(s"Adding ${filename.value} - ${githubContent.formTemplateId} to cache")
        cache.put(filename, githubContent)
      }
    }
  }

  def refreshCache: RefreshCache.Progress = cacheStatus match {
    case CacheStatus.RefreshInProgress(progress, total, commit) => RefreshCache.Progress(progress, total, commit)
    case CacheStatus.Refreshed(_) | CacheStatus.Uninitialized | CacheStatus.Failed =>
      val shutdownLatch = new CountDownLatch(1)

      val populateCache: EitherT[IO, String, Unit] = for {
        lastCommit      <- githubService.lastCommit
        githubTemplates <- githubService.listTemplates
        _               <- retrieveTemplates(lastCommit, shutdownLatch, githubTemplates)
        lastCommit2     <- githubService.lastCommit
      } yield
        if (lastCommit != lastCommit2) {
          refreshCommit = None
        } else ()

      populateCache
        .fold(
          error => {
            refreshCommit = None
            numberOfTemplates.set(0)
            shutdownLatch.countDown()
            logger.error(s"Problem when populating cache: $error")
          },
          _ => logger.info(s"Cache populated succesfully")
        )
        .unsafeRunAsync {
          case Left(error) =>
            refreshCommit = None
            numberOfTemplates.set(0)
            shutdownLatch.countDown()
            logger.error(s"[unsafeRunAsync] Problem when populating cache: ${error.getMessage}", error)
          case Right(()) => logger.info(s"[unsafeRunAsync] Cache populated succesfully")
        }
      shutdownLatch.await()
      RefreshCache.Progress(0, numberOfTemplates.get, refreshCommit)
  }
}
