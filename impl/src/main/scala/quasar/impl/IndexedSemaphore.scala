/*
 * Copyright 2014–2019 SlamData Inc.
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

package quasar.impl

import slamdata.Predef._

import cats.effect.{Concurrent, ContextShift, Sync}
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.syntax.bracket._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.~>

abstract class IndexedSemaphore[F[_]: Sync, I] {
  def get(i: I): F[Semaphore[F]]
}

object IndexedSemaphore {
  def apply[F[_]: Concurrent: ContextShift, I]: F[IndexedSemaphore[F, I]] = {
    for {
      semaphores <- Ref.of[F, Map[I, Semaphore[F]]](Map.empty)
      mainSemaphore <- Semaphore[F](1)
    } yield {
      val inMain: F ~> F = λ[F ~> F] { fa =>
        (mainSemaphore.acquire *> fa).guarantee(mainSemaphore.release)
      }
      new IndexedSemaphore[F, I] {
        def get(i: I): F[Semaphore[F]] =
          inMain { semaphores.get flatMap { ss =>
            ss.get(i) match {
              case None => for {
                s <- Semaphore[F](1)
                _ <- semaphores.update(m => m.updated(i, s))
              } yield s
              case Some(s) => s.pure[F]
            }}}
      }
    }
  }
}
