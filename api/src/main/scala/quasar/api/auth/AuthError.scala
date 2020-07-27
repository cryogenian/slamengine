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

import scala.{Product, Serializable, Nothing}

trait AuthError[+S, +B] extends Product with Serializable

object AuthError {
  final case class InvalidScheme[S](scheme: S) extends AuthError[S, Nothing]
  final case class CredentialsNotFound[B](subject: B) extends AuthError[Nothing, B]
}