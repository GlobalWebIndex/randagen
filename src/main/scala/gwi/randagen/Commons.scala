package gwi.randagen

import java.{util, lang}
import org.apache.commons.math3.distribution.{EnumeratedDistribution, RealDistribution}
import org.apache.commons.math3.util.Pair
import scala.util.{Failure, Success, Try}

object Commons {

  def apply(className: String, args: Seq[Double]): RealDistribution = Try {
    Class
      .forName(className)
      .getConstructor(args.map(_ => classOf[Double]):_*)
      .newInstance(args.map(new lang.Double(_)): _*)
  } match {
    case Success(instance) => instance.asInstanceOf[RealDistribution]
    case Failure(ex) => throw new IllegalArgumentException(s"Unable to instantiate $className with arguments ${args.mkString(",")} !!!", ex)
  }

  def enumeratedDistro[T](pmf: util.ArrayList[Pair[T, lang.Double]]): EnumeratedDistribution[T] = new EnumeratedDistribution(pmf)

  def enumeratedDistro[T](pmf: Array[(T, Double)]): EnumeratedDistribution[T] = {
    val arrList = new util.ArrayList[Pair[T, lang.Double]](pmf.length)
    pmf.foreach { case (e, p) => arrList.add(new Pair(e, new lang.Double(p))) }
    enumeratedDistro(arrList)
  }

  implicit class RealDistributionPimp(underlying: RealDistribution) {
    def getPMF(size: Int): util.ArrayList[Pair[Double, lang.Double]] = {
      val dataPoints = underlying.sample(size)
      val arrList = new util.ArrayList[Pair[Double, lang.Double]](dataPoints.length)
      for (idx <- 0 until dataPoints.length) {
        val dataPoint = dataPoints(idx)
        arrList.add(new Pair(dataPoint, new lang.Double(underlying.cumulativeProbability(dataPoint))))
      }
      arrList
    }
  }

}