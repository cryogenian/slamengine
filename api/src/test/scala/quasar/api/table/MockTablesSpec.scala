/*
 * Copyright 2014–2018 SlamData Inc.
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

package quasar.api.table

import cats.data.StateT
import cats.effect.IO
import java.util.UUID
import quasar.contrib.scalaz.MonadState_
import scalaz.{~>, Id, IMap, Monad}, Id.Id
import scalaz.std.string._
import shims._
import slamdata.Predef._

import MockTablesSpec.Store
import MockTables._

final class MockTablesSpec extends TablesSpec[StateT[IO, Store, ?], List, UUID, String, String] {

  // TODO why doesn't this resolve if I put it anywhere but here
  implicit def monadState[F[_]: Monad, S] =
    new MonadState_[StateT[F, S, ?], S] {
      def get = StateT.get
      def put(s: S) = StateT.set(s)
    }

  val tables: Tables[StateT[IO, Store, ?], List, UUID, String, String] =
    MockTables[StateT[IO, Store, ?]]

  val table1: Table[String] = Table(TableName("table1"), "select * from table1")
  val table2: Table[String] = Table(TableName("table2"), "select * from table2")

  val preparation1: String = table1.query
  val preparation2: String = table2.query

  val uniqueId: UUID = UUID.randomUUID

  def toList[A](as: List[A]): List[A] = as

  def run: StateT[IO, Store, ?] ~> Id.Id =
    λ[StateT[IO, Store, ?] ~> Id](
      _.runA(IMap.empty).unsafeRunSync)
}

object MockTablesSpec {
  type Store = IMap[UUID, MockTables.MockTable]
}
