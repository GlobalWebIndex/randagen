package gwi.randagen

import java.{lang, util}

import org.apache.commons.math3.distribution.{IntegerDistribution, RealDistribution, EnumeratedDistribution}
import org.apache.commons.math3.util.Pair

import scala.util.{Failure, Success, Try}

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

  def apply(className: String, args: Seq[Double]): Any = Try {
    Class
      .forName(className)
      .getConstructor(args.map(_ => classOf[Double]):_*)
      .newInstance(args.map(new lang.Double(_)): _*)
  } match {
    case Success(instance) => instance
    case Failure(ex) => throw new IllegalArgumentException(s"Unable to instantiate $className with arguments ${args.mkString(",")} !!!", ex)
  }

  def realDistro(className: String, args: Seq[Double]) = new RealDistro(Commons(className: String, args: Seq[Double]).asInstanceOf[RealDistribution])
  def intDistro(className: String, args: Seq[Double]) = new IntDistro(Commons(className: String, args: Seq[Double]).asInstanceOf[IntegerDistribution])

  def enumeratedDistro[T](pmf: util.ArrayList[Pair[T, lang.Double]]): EnumeratedDistribution[T] = new EnumeratedDistribution(pmf)

  def enumeratedDistro[T](pmf: Array[(T, Double)]): EnumeratedDistribution[T] = {
    val arrList = new util.ArrayList[Pair[T, lang.Double]](pmf.length)
    pmf.foreach { case (e, p) => arrList.add(new Pair(e, new lang.Double(p))) }
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