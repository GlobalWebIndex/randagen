package gwi.randagen

import java.lang

import scala.annotation.tailrec
import scala.util.{Failure, Random, Success, Try}

/**
  * After I wrote this I found that it is `org.apache.commons.math3.distribution.EnumeratedDistribution`
  *
  * Anyway, it seems to be faster for some reason and precise exactly the same ... I will investigate further
  *
  * @param weightedElements probability distribution function - elements and weights that determine resulting probability distribution
  * @return element with probability influenced by given weightings
  */
case class EnumeratedDistro[T](weightedElements: Seq[(T, Double)]) {
  val probabilitySum = weightedElements.map(_._2).sum // adding up at init time for performance reasons

  def sample: T = {
    val randomSeed =
      if (probabilitySum == 1.0) {
        Random.nextDouble()
      } else {
        Random.nextDouble() * probabilitySum
      }
    @tailrec
    def recursively(it: Iterator[(T, Double)], acc: Double): T = {
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

object CommonsDistribution {

  import org.apache.commons.math3.distribution.RealDistribution

  def apply(className: String, arg1: Double, arg2: Double): RealDistribution = Try {
    val args = Seq[lang.Double](new lang.Double(arg1), new lang.Double(arg2))
    Class
      .forName(className)
      .getConstructor(classOf[Double], classOf[Double])
      .newInstance(args: _*)
  } match {
    case Success(instance) => instance.asInstanceOf[RealDistribution]
    case Failure(ex) => throw new IllegalArgumentException(s"Unable to instantiate $className with arguments $arg1 and $arg2 !!!", ex)
  }

  implicit class RealDistributionPimp(underlying: RealDistribution) {
    def getProbabilityDistribution(size: Int): Seq[(Double, Double)] =
      underlying.sample(size).map(dataPoint => dataPoint -> underlying.cumulativeProbability(dataPoint))
  }

}