package gwi.randagen

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

class ArrayUtilsTestSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import gwi.randagen.ArrayUtils.IntArrayPimp

  "flattening array should work" in {
    val target =
      Array(
        Array('a', 'b', 'c').map(_.toByte),
        Array('d', 'e', 'f').map(_.toByte),
        Array('g', 'h', 'i').map(_.toByte)
      )

    val expected = Array('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i').map(_.toByte)
    val actual = ArrayUtils.flattenArray(target)
    assertResult(expected)(actual)
  }

  "ranging array should work" in {
    assertResult(Array(0,1,2)) (ArrayUtils.range(3))
    assertResult(Array(0)) (ArrayUtils.range(1))
    assertResult(Array()) (ArrayUtils.range(0))
  }

  "array partitions should work" in {
    assertResult(List((0,11))) ((0 until 12).toArray.arrayPartitions(1))
    assertResult(List((0,5), (6,11))) ((0 until 12).toArray.arrayPartitions(2))
    assertResult(List((0,3), (4,7), (8,11))) ((0 until 12).toArray.arrayPartitions(3))
    assertResult(List((0,2), (3,5), (6,8), (9,11))) ((0 until 12).toArray.arrayPartitions(4))
    assertResult(List((0,2), (3,5), (6,7), (8,9), (10,11))) ((0 until 12).toArray.arrayPartitions(5))
    assertResult(List((0,1), (2,3), (4,5), (6,7), (8,9), (10,11))) ((0 until 12).toArray.arrayPartitions(6))
    assertThrows[IllegalArgumentException]((0 until 12).toArray.arrayPartitions(7))
  }

  "array iterators should work" in {
    assertResult(List((0,0), (1,1), (2,2))) (Array(0,1,2,3).arrayIterator((0,2)).toList)
    assertResult(List((0,0))) (Array(0,1).arrayIterator((0,0)).toList)
    assertThrows[IllegalArgumentException](Array(0,1).arrayIterator((0,2)).toList)
  }

  "array shuffle should work" in {
    assert(Array(1,2,3).shuffle.length == 3)
    assertResult(Set(1,2,3)) (Array(1,2,3).shuffle.toSet)
    assertResult(Array(1)) (Array(1).shuffle)
    assertResult(Array()) (Array[Int]().shuffle)
  }

  "map async should work" in {
    val f2 = ArrayUtils.range(6).mapAsync(3)(_.toList)
    whenReady(f2) { r =>
      assert(r.length == 3)
      assertResult(List(List((0,0), (1,1)), List((2,2), (3,3)), List((4,4), (5,5)))) (r)
    }
  }

}
