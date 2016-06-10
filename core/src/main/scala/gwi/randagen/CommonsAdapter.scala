package gwi.randagen

import java.{lang, util}

import org.apache.commons.math3.distribution.{IntegerDistribution, RealDistribution, EnumeratedDistribution}
import org.apache.commons.math3.util.Pair

sealed trait Distro[T] {
  def sample: T
  def sample(size: Int): Array[T]
  def cumulativeProbability(x: T): Double
}

class RealDistro(underlying: RealDistribution) extends Distro[Double] {
  def sample: Double = underlying.sample()
  def sample(size: Int): Array[Double] = underlying.sample(size)
  def cumulativeProbability(x: Double): Double = underlying.cumulativeProbability(x)
}

class IntDistro(underlying: IntegerDistribution) extends Distro[Int] {
  def sample: Int = underlying.sample()
  def sample(size: Int): Array[Int] = underlying.sample(size)
  def cumulativeProbability(x: Int): Double = underlying.cumulativeProbability(x)
}

object Commons {

  def enumeratedDistro[T](pmf: util.ArrayList[Pair[T, lang.Double]]): EnumeratedDistribution[T] = new EnumeratedDistribution(pmf)

  def enumeratedDistro[T](pmf: Array[(T, Double)]): EnumeratedDistribution[T] = {
    val arrList = new util.ArrayList[Pair[T, lang.Double]](pmf.length)
    for (idx <- pmf.indices) {
      val (elm, prob) = pmf(idx)
      arrList.add(new Pair(elm, new lang.Double(prob)))
    }
    enumeratedDistro(arrList)
  }

  implicit class RealDistributionPimp[T](underlying: Distro[T]) {
    def getPMF(size: Int): util.ArrayList[Pair[T, lang.Double]] = {
      val dataPoints = underlying.sample(size)
      val arrList = new util.ArrayList[Pair[T, lang.Double]](dataPoints.length)
      for (idx <- dataPoints.indices) {
        val dataPoint = dataPoints(idx)
        arrList.add(new Pair(dataPoint, new lang.Double(underlying.cumulativeProbability(dataPoint))))
      }
      arrList
    }
  }

}