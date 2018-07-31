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

package quasar.mimir.evaluate

import slamdata.Predef._

import quasar._
import quasar.connector.QScriptEvaluator
import quasar.contrib.cats.effect.liftio._
import quasar.contrib.iota._
import quasar.contrib.pathy._
import quasar.contrib.scalaz.MonadTell_
import quasar.fp._
import quasar.fp.numeric._
import quasar.mimir
import quasar.mimir.{MimirQScriptCP, MimirRepr}
import quasar.mimir.MimirCake._
import quasar.qscript._
import quasar.qscript.rewrites.{Optimize, Unicoalesce, Unirewrite}

import scala.Predef.implicitly

import cats.effect.{IO, LiftIO}
import fs2.Stream
import iotaz.CopK
import matryoshka._
import matryoshka.implicits._
import scalaz._
import scalaz.syntax.monad._
import shims._

final class MimirQScriptEvaluator[
    T[_[_]]: BirecursiveT: EqualT: ShowT: RenderTreeT,
    F[_]: LiftIO: Monad: MonadPlannerErr: MonadTell_[?[_], List[IO[Unit]]]] private (
    cake: Cake)
    extends QScriptEvaluator[T, AssociatesT[T, F, IO, ?], Stream[IO, MimirRepr]] {

  type MT[X[_], A] = Kleisli[X, Associates[T, IO], A]
  type M[A] = MT[F, A]

  type QS[U[_[_]]] = MimirQScriptCP[U]

  type Repr = MimirRepr

  implicit def QSMFromQScriptCoreI: Injectable[QScriptCore[T, ?], QSM] =
    Injectable.inject[QScriptCore[T, ?], QSM]

  implicit def QSMFromEquiJoinI: Injectable[EquiJoin[T, ?], QSM] =
    Injectable.inject[EquiJoin[T, ?], QSM]

  implicit def QSMFromShiftedReadI: Injectable[Const[ShiftedRead[AFile], ?], QSM] =
    Injectable.inject[Const[ShiftedRead[AFile], ?], QSM]

  implicit def QSMToQScriptTotal: Injectable[QSM, QScriptTotal[T, ?]] =
    mimir.qScriptToQScriptTotal[T]

  def QSMFunctor: Functor[QSM] =
    Functor[QSM]

  def QSMFromQScriptCore: QScriptCore[T, ?] :<<: QSM =
    CopK.Inject[QScriptCore[T, ?], QSM]

  def UnirewriteT: Unirewrite[T, QS[T]] =
    implicitly[Unirewrite[T, QS[T]]]

  def UnicoalesceCap: Unicoalesce.Capture[T, QS[T]] =
    Unicoalesce.Capture[T, QS[T]]

  def execute(repr: Repr): M[Stream[IO, Repr]] =
    Stream(repr).covary[IO].point[M]

  def optimize: QSM[T[QSM]] => QSM[T[QSM]] =
    (new Optimize[T]).optimize(reflNT[QSM])

  def plan(cp: T[QSM]): M[Repr] = {
    def qScriptCorePlanner =
      new mimir.QScriptCorePlanner[T, M](cake)

    def equiJoinPlanner =
      new mimir.EquiJoinPlanner[T, M]

    def shiftedReadPlanner =
      new FederatedShiftedReadPlanner[T, F](cake)

    lazy val planQST: AlgebraM[M, QScriptTotal[T, ?], Repr] = {
      val QScriptCore = CopK.Inject[QScriptCore[T, ?],            QScriptTotal[T, ?]]
      val EquiJoin    = CopK.Inject[EquiJoin[T, ?],               QScriptTotal[T, ?]]
      val ShiftedRead = CopK.Inject[Const[ShiftedRead[AFile], ?], QScriptTotal[T, ?]]
      _ match {
        case QScriptCore(value) => qScriptCorePlanner.plan(planQST)(value)
        case EquiJoin(value)    => equiJoinPlanner.plan(planQST)(value)
        case ShiftedRead(value) => shiftedReadPlanner.plan(value)
        case _ => ???
      }
    }

    def planQSM(in: QSM[Repr]): M[Repr] = {
      val QScriptCore = CopK.Inject[QScriptCore[T, ?],            QSM]
      val EquiJoin    = CopK.Inject[EquiJoin[T, ?],               QSM]
      val ShiftedRead = CopK.Inject[Const[ShiftedRead[AFile], ?], QSM]

      in match {
        case QScriptCore(value) => qScriptCorePlanner.plan(planQST)(value)
        case EquiJoin(value)    => equiJoinPlanner.plan(planQST)(value)
        case ShiftedRead(value) => shiftedReadPlanner.plan(value)
      }
    }

    cp.cataM[M, Repr](planQSM _)
  }
}

object MimirQScriptEvaluator {
  def apply[
      T[_[_]]: BirecursiveT: EqualT: ShowT: RenderTreeT,
      F[_]: LiftIO: Monad: MonadPlannerErr: MonadTell_[?[_], List[IO[Unit]]]](
      cake: Cake)
      : QScriptEvaluator[T, AssociatesT[T, F, IO, ?], Stream[IO, MimirRepr]] =
    new MimirQScriptEvaluator[T, F](cake)
}
