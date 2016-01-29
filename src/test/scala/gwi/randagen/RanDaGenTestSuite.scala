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
    val actual = Utils.flattenArray(target)
    assertResult(expected)(actual)
  }

}
