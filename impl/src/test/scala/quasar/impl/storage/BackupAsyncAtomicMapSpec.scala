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

package quasar.impl.storage

import slamdata.Predef._

import cats.effect.{IO, Resource}
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.instances.list._

import quasar.concurrent.BlockingContext

import io.atomix.core._
import io.atomix.core.election._
import io.atomix.core.map._
import io.atomix.cluster.discovery._
import io.atomix.cluster._
import io.atomix.protocols.backup.partition._
import io.atomix.protocols.raft.partition._
import io.atomix.protocols.raft.storage.RaftStorage
import io.atomix.storage.StorageLevel
import org.mapdb._

import scalaz.std.string._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import java.nio.file.{Paths, Path => NPath}
import java.util.concurrent.ConcurrentHashMap

import org.specs2.specification._

import shims._

import AtomixSetup._

final class BackupAsyncAtomicMapSpec extends IndexedStoreSpec[IO, String, String] with AfterAll {
  sequential

  val pool = BlockingContext.cached("mapdb-async-atomic-map")

  val DefaultNode = NodeInfo("default", "localhost", 5000)
  val DefaultPath = Paths.get("default.raft")
  val DefaultMapName = "default"

  val emptyStore: Resource[IO, IndexedStore[IO, String, String]] =
    Resource.liftF(IO(new ConcurrentHashMap[String, String]())) flatMap { backup =>
      BackupStore[IO, String, String](
        DefaultMapName,
        backup,
        pool,
        DefaultNode,
        List(DefaultNode),
        DefaultPath)
    }

//  def mkAtomix(memberId: String, host: String, port: Int) =
//    Atomix.builder()
//      .withMemberId(memberId)
//      .withAddress(host, port)
//      .withManagementGroup(PrimaryBackupPartitionGroup.builder("system").withNumPartitions(1).build())
//      .withPartitionGroups(PrimaryBackupPartitionGroup.builder("data").withNumPartitions(32).build())

//  val atomix: Atomix =
//    mkAtomix("member-1", "localhost", 5000).build()

  def afterAll: Unit = () //emptyStorePair.flatMap({
//    case (_, fin) => fin
//  }).unsafeRunSync

/*
  def mkStore(
      atomix: Atomix,
      mapName: String,
      mMap: ConcurrentHashMap[String, String])
      : IO[BackupStore[IO, String, String]] = for {
    aMap <- IO{
      atomix.atomicMapBuilder[String, String](mapName)
        .build()
        .async()
    }
  } yield BackupStore(BackupAsyncAtomicMap[IO, String, String](aMap, mMap, pool))


  def mkStore(
      atomix: Atomix,
      mapName: String,
      mMap: ConcurrentHashMap[String, String],
      boolean: Boolean)
      : IO[BackupStore[IO, String, String]] = for {
    aMap <- IO{
      atomix.atomicMapBuilder[String, String](mapName)
        .build()
        .async()
    }
  } yield BackupStore(BackupAsyncAtomicMap[IO, String, String](aMap, mMap, pool))
 */
//  val emptyStore: IO[IndexedStore[IO, String, String]] = emptyStorePair map {
//    case (store, _) => store
//  }

  val valueA = "A"
  val valueB = "B"
  val freshIndex = IO(Random.nextInt().toString)
/*
  "restore" >>* {
    val atomix1 = mkAtomix("member-1", "localhost", 5004)
      .build()

    val prefilled = {
      val res = new ConcurrentHashMap[String, String]()
      res.put("prefilled-key", "prefilled")
      res
    }

    for {
      _ <- IO(atomix1.start().join())
      store1 <- mkStore(atomix1, "map", prefilled, true)
      _ <- store1.restore
      pv <- store1.lookup("prefilled-key")
      _ <- IO(atomix1.stop().join())
    } yield {
      pv === Some("prefilled")
    }
  }

  "joining and rejoining" >>* {
    val atomix1 =
      Atomix.builder()
        .withMemberId("111")
        .withAddress("localhost", 5010)
        .withManagementGroup(PrimaryBackupPartitionGroup.builder("system").withNumPartitions(1).build())
        .withPartitionGroups(RaftPartitionGroup
          .builder("data")
          .withMembers("111")
          .withStorageLevel(StorageLevel.DISK)
          .withDataDirectory(Paths.get(java.util.UUID.randomUUID().toString().concat(".raft")).toFile)
          .withNumPartitions(32)
          .build())
        .withMembershipProvider(BootstrapDiscoveryProvider.builder()
          .withNodes(Node.builder().withAddress("localhost", 5011).build()).build())
        .build()

    val atomix2 =
      Atomix.builder()
        .withMemberId("222")
        .withAddress("localhost", 5011)
        .withManagementGroup(PrimaryBackupPartitionGroup.builder("system").withNumPartitions(1).build())
        .withPartitionGroups(RaftPartitionGroup
          .builder("data")
          .withMembers("111", "222")
          .withStorageLevel(StorageLevel.DISK)
          .withDataDirectory(Paths.get(java.util.UUID.randomUUID().toString().concat(".raft")).toFile)
          .withNumPartitions(32)
          .build())
        .build

    for {
      persistent1 <- IO(new ConcurrentHashMap[String, String]())
      persistent2 <- IO(new ConcurrentHashMap[String, String]())

      _ <- AsyncAtomicIndexedStore.toF[IO, java.lang.Void](atomix1.start)
      _ <- IO(println("ATOMIX 1"))
      store1 <- mkStore(atomix1, "map", persistent1)
      a <- IO(java.lang.System.currentTimeMillis())
      _ <- store1.insert("a", "b")
      b <- IO(java.lang.System.currentTimeMillis())
      _ <- IO(println(b - a))

      _ <- AsyncAtomicIndexedStore.toF[IO, java.lang.Void](atomix2.start)
      _ <- IO(println("ATOMIX 2"))
      store2 <- mkStore(atomix2, "map", persistent2)


      a <- IO(java.lang.System.currentTimeMillis())
      o1 <- store1.lookup("a")
      b <- IO(java.lang.System.currentTimeMillis())
      o2 <- store2.lookup("a")
      _ <- IO(println(b - a))
      _ <- IO(println(s"o1 ::: ${o1}    o2 ::: ${o2}"))

      a <- IO(java.lang.System.currentTimeMillis())
      _ <- store1.insert("b", "c")
      b <- IO(java.lang.System.currentTimeMillis())
      _ <- IO(println(b - a))

      a <- IO(java.lang.System.currentTimeMillis())
      _ <- store1.lookup("a")
      b <- IO(java.lang.System.currentTimeMillis())
      _ <- IO(println(b - a))

      o1 <- store1.lookup("a")
      o2 <- store2.lookup("a")
      _ <- IO(println(s"o1 ::: ${o1}    o2 ::: ${o2}"))
      o1 <- store1.lookup("b")
      o2 <- store2.lookup("b")
      _ <- IO(println(s"o1 ::: ${o1}    o2 ::: ${o2}"))

      _ <- AsyncAtomicIndexedStore.toF[IO, java.lang.Void](atomix2.stop)
      _ <- AsyncAtomicIndexedStore.toF[IO, java.lang.Void](atomix1.stop)
    } yield {
      true
    }
  }


  "stop and run" >>* {
      def mkAtomix(id: String, port: Int, connects: List[(String, Int)]): Atomix = {
      val ids: List[String] = connects map (_._1)
      val ports: List[Int] = connects map (_._2)
      val nodes = connects map { case (id, port) => Node.builder.withId(id).withAddress("localhost", port).build }
      Atomix.builder
        .withMemberId(id)
        .withAddress("localhost", port)
        .withManagementGroup(PrimaryBackupPartitionGroup.builder("system").withNumPartitions(1).build())
        .withPartitionGroups(RaftPartitionGroup
          .builder("data")
          .withMembers("1")
          .withStorageLevel(StorageLevel.DISK)
          .withDataDirectory(Paths.get(s"${java.util.UUID.randomUUID.toString}.raft").toFile)
          .withNumPartitions(32)
          .build)
        .withMembershipProvider(BootstrapDiscoveryProvider.builder.withNodes(nodes:_*).build)
        .build
    }

    val ports = List(("1", 5020), ("2", 5021), ("3", 5022), ("4", 5023))

    val atomix1 = () => mkAtomix("1", 5020, ports)
    val atomix2 = () => mkAtomix("2", 5021, ports)
    val atomix3 = () => mkAtomix("3", 5022, ports)
    val atomix4 = () => mkAtomix("4", 5023, ports)

    def start(a: Atomix): IO[Atomix] = AsyncAtomicIndexedStore.toF[IO, java.lang.Void](a.start) as a
    def stop(a: Atomix): IO[Unit] = AsyncAtomicIndexedStore.toF[IO, java.lang.Void](a.stop) as (())

    for {
      persistent1 <- IO(new ConcurrentHashMap[String, String]())
      persistent2 <- IO(new ConcurrentHashMap[String, String]())
      persistent3 <- IO(new ConcurrentHashMap[String, String]())
      persistent4 <- IO(new ConcurrentHashMap[String, String]())

      as <- List(start(atomix1()), start(atomix2())).parSequence
      _ <- IO(println("uno"))
      store1 <- mkStore(as(0), "map", persistent1)
      store2 <- mkStore(as(1), "map", persistent2)
      a2 <- List(store1.lookup("foo"), store2.lookup("foo")).parSequence
      _ <- IO(println(s"a2 ::: ${a2}"))
      _ <- store1.insert("foo", "2")
      a2 <- List(store1.lookup("foo"), store2.lookup("foo")).parSequence
      _ <- IO(println(s"a2 ::: ${a2}"))
      _ <- (as map (stop(_))).parSequence
      _ <- IO(println("duo"))

      as <- List(start(atomix1()), start(atomix2()), start(atomix3()), start(atomix4())).parSequence
      store1 <- mkStore(as(0), "map", persistent1, true)
      store2 <- mkStore(as(1), "map", persistent2)
      store3 <- mkStore(as(2), "map", persistent3)
      store4 <- mkStore(as(3), "map", persistent4)

      _ <- store3.insert("foo", "1")
      a1 <- List(store1.lookup("foo"), store2.lookup("foo"), store3.lookup("foo"), store4.lookup("foo")).parSequence
      _ <- (as map (stop(_))).parSequence
      _ <- IO(println(s"a1 ::: ${a1}"))

      as <- List(start(atomix1()), start(atomix2())).parSequence
      _ <- IO(println("SECOND"))
      _ <- IO(println(persistent1.entrySet))
      store1 <- mkStore(as(0), "map", persistent1, true)
      store2 <- mkStore(as(1), "map", persistent2)
      a2 <- List(store1.lookup("foo"), store2.lookup("foo")).parSequence
      _ <- IO(println(s"a2 ::: ${a2}"))
      _ <- store1.insert("foo", "2")
      a3 <- List(store1.lookup("foo"), store2.lookup("foo")).parSequence
      _ <- IO(println(s"a3 ::: ${a3}"))
      _ <- (as map (stop(_))).parSequence
    } yield true

  }

  "three stores connected" >>* {
    val atomix1 = mkAtomix("member-1", "localhost", 5001)
      .build()

    val atomix2 = mkAtomix("member-2", "localhost", 5002)
      .withMembershipProvider(BootstrapDiscoveryProvider.builder()
        .withNodes(Node.builder().withAddress("localhost", 5001).build()).build())
      .build()

    val atomix3 = mkAtomix("member-3", "localhost", 5003)
      .withMembershipProvider(BootstrapDiscoveryProvider.builder()
        .withNodes(Node.builder().withAddress("localhost", 5001).build()).build())
      .build()


    for {
      _ <- IO(atomix1.start().join())
      _ <- IO(atomix2.start().join())
      store1 <- mkStore(atomix1, "map", new ConcurrentHashMap())
      store2 <- mkStore(atomix2, "map", new ConcurrentHashMap())

      _ <- store1.insert("foo", "bar")
      _ <- store2.insert("foo", "baz")

      _ <- IO(atomix3.start().join())
      store3 <- mkStore(atomix3, "map", new ConcurrentHashMap())
      o <- store3.lookup("foo")
      _ <- store3.insert("foo", "quux")

      a1 <- store1.lookup("foo")
      a2 <- store2.lookup("foo")
      a3 <- store3.lookup("foo")
      _ <- IO(atomix1.stop().join())
      _ <- IO(atomix2.stop().join())
      _ <- IO(atomix3.stop().join())
    } yield {
      a1 === Some("quux")
      a2 === Some("quux")
      a3 === Some("quux")
    }
  }
 */
}