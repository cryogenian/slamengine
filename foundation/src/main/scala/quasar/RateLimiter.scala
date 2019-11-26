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

package quasar

import slamdata.Predef._

import quasar.contrib.cats.hash.toHashing

import java.util.concurrent.TimeUnit

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.math.Equiv
import scala.reflect.{ClassTag, classTag}

import cats.effect.{Sync, Timer}
import cats.effect.concurrent.Ref
import cats.kernel.{Eq, Hash}
import cats.implicits._

import skolems._

final class RateLimiter[F[_]: Sync: Timer] private () {
  // TODO make these things clustering-aware
  private val configs: TrieMap[Exists[Key], Config] =
    new TrieMap[Exists[Key], Config](
      toHashing[Exists[Key]],
      RateLimiter.toEquiv[Exists[Key]])

  private val states: TrieMap[Exists[Key], Ref[F, State]] =
    new TrieMap[Exists[Key], Ref[F, State]](
      toHashing[Exists[Key]],
      RateLimiter.toEquiv[Exists[Key]])

  def apply[A: Hash: ClassTag](key: A, max: Int, caution: Double, window: FiniteDuration)
      : F[F[Unit]] =
    for {
      config <- Sync[F] delay {
        val c = Config(max, caution, window)
        configs.putIfAbsent(Key(key, Hash[A], classTag[A]), c).getOrElse(c)
      }

      now <- nowF
      maybeR <- Ref.of[F, State](State(0, now))
      stateRef <- Sync[F] delay {
        states.putIfAbsent(Key(key, Hash[A], classTag[A]), maybeR).getOrElse(maybeR)
      }
    } yield limit(config, stateRef)

  private def limit(config: Config, stateRef: Ref[F, State]): F[Unit] = {
    import config._

    val emptyStateF: F[Boolean] =
      nowF.flatMap(now => stateRef.tryUpdate(_ => State(0, now)))

    for {
      now <- nowF
      state <- stateRef.get
      back <-
        if (state.start + window < now) {
          emptyStateF >> limit(config, stateRef)
        } else {
          stateRef.modify(s => (s.copy(count = s.count + 1), s.count)) flatMap { count =>
            if (count >= max * caution) {
              Timer[F].sleep((state.start + window) - now) >>
                emptyStateF >>
                limit(config, stateRef)
            } else
              ().pure[F]
          }
        }
    } yield back
  }

  private val nowF: F[FiniteDuration] =
    Timer[F].clock.realTime(TimeUnit.MILLISECONDS).map(_.millis)

  private final case class Config(max: Int, caution: Double, window: FiniteDuration)
  private final case class State(count: Int, start: FiniteDuration)

  private final case class Key[A](value: A, hash: Hash[A], tag: ClassTag[A])

  private object Key {
    implicit def hash: Hash[Exists[Key]] =
      new Hash[Exists[Key]] {

        def hash(k: Exists[Key]) =
          k.apply().hash.hash(k.apply().value)

        def eqv(left: Exists[Key], right: Exists[Key]) = {
          (left.apply().tag == right.apply().tag) &&
            left.apply().hash.eqv(
              left.apply().value,
              right.apply().value.asInstanceOf[left.A])
        }
      }
  }
}

object RateLimiter {
  def apply[F[_]: Sync: Timer]: RateLimiter[F] =
    new RateLimiter[F]

  def toEquiv[A: Eq]: Equiv[A] =
    Equiv.fromFunction(Eq[A].eqv(_, _))
}
