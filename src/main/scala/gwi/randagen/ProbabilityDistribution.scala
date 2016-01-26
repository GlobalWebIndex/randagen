package gwi.randagen

import scala.annotation.tailrec
import scala.util.Random

object ProbabilityDistribution {

  /**
    * @param weightedElements elements with weights that determine resulting probability distribution
    * @param probabilityDistribution whether or not weights represent probability distribution (their sum == 1.0) - performance reasons
    * @return element with probability influenced by given weightings
    */
  def sampleElementFrom(weightedElements: Seq[(String, Double)], probabilityDistribution: Boolean): String = {
    val randomSeed =
      if (probabilityDistribution) {
        require(weightedElements.map(_._2).sum == 1.0, "Probabilities of elements must sum to 1.0 !!!")
        Random.nextDouble()
      } else {
        Random.nextDouble() * weightedElements.map(_._2).sum
      }
    @tailrec
    def recursively(it: Iterator[(String, Double)], acc: Double): String = {
      if (it.hasNext) {
        val (nextKey, nextProb) = it.next
        val nextAcc = acc + nextProb
        if (nextAcc >= randomSeed)
          nextKey
        else
          recursively(it, nextAcc)
      } else
        throw new IllegalArgumentException(s"If you choose ")
    }
    recursively(weightedElements.iterator, 0.0)
  }

}
