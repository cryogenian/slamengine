package blueeyes.persistence.mongo

import java.util.concurrent.CountDownLatch
import org.specs.Specification
import org.specs.mock.MocksCreation
import MongoQueryBuilder._
import org.mockito.Mockito.{times, when}
import org.mockito.Mockito
import blueeyes.json.JsonAST._
import blueeyes.json.JPath

class EnsureIndexQueryBehaviourSpec extends Specification with MocksCreation{
  private val collection  = mock[DatabaseCollection]
  "Call collection method" in{
    when(collection.getLastError).thenReturn(None)

    val query  = ensureUniqueIndex("index").on("address.city", "address.street").in("collection")
    val result = query(collection)
//    val countDown = new CountDownLatch(1)
//
//    result.deliverTo{v => countDown.countDown()}
//    countDown.await()

    Mockito.verify(collection, times(1)).ensureIndex("index", JPath("address.city") :: JPath("address.street") :: Nil, true)

    result must be (JNothing)
  }
}