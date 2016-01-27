package gwi.randagen

import org.scalatest.{FreeSpec, Matchers}

class RanDaGenTestSuite extends FreeSpec with Matchers {

  "flattening array should work" in {
    val target =
      List(
        Array('a', 'b', 'c').map(_.toByte),
        Array('d', 'e', 'f').map(_.toByte),
        Array('g', 'h', 'i').map(_.toByte)
      )

    val expected = Array('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i').map(_.toByte)
    val actual = RanDaGen.flattenArray(target)
    assertResult(expected)(actual)
  }

  "weighted sample should not cross 2 percent deviation" in {
    def assertPlusMinus(expected: Int, actual: Int)(deviation: Int) = assert(Math.abs(expected-actual) < deviation)

    val dist = EnumeratedDistro(Seq("B" -> 0.3, "A" -> 0.5, "C" -> 0.2))
    val valueOccurrence = (0 to 10000)
      .map(idx => dist.sample)
      .groupBy(identity)
      .mapValues(_.size)

    // 2% deviation is not tolerated !!
    assertPlusMinus(5000, valueOccurrence("A"))(200)
    assertPlusMinus(3000, valueOccurrence("B"))(200)
    assertPlusMinus(2000, valueOccurrence("C"))(200)
  }
}
