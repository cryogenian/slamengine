/*
 * Copyright 2020 Precog Data
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

package quasar.api.auth

import scala._

trait Credentials[+F[_]] extends Product with Serializable

object Credentials {
  final case class Perpetual[F[_]](get: F[Array[Byte]]) extends Credentials[F]
  final case class Temporary[F[_]](get: F[Array[Byte]], renew: F[Unit]) extends Credentials[F]
  final case object Omitted extends Credentials[Nothing]

  def omitted[F[_]]: Credentials[F] = Omitted
}