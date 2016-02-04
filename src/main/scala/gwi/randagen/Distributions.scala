package gwi.randagen

import gwi.randagen.Commons.RealDistributionPimp
import org.apache.commons.math3.distribution.{RealDistribution, IntegerDistribution}

/**
  * Distribution creates Probability Mass Function based on input arguments and it is being sampled for every event
  *
  * @note that Distribution implementations should be stateless and idempotent.
  *       For instance a single field can be generated 1000 times (1000 columns) sharing a single Mapper
  *       So that calling generator 1000 times for a single row shouldn't affect results at all
  */
sealed trait Distribution[+T] {
  def sample(progress: Progress): T
}

object Linear extends Distribution[Int] {
  def sample(progress: Progress): Int = progress.idx
}

case class Constant[T](v: T) extends Distribution[T] {
  def sample(progress: Progress): T = v
}

case class Random(cardinalityRatio: Int = 100) extends Distribution[Int] {
  require(cardinalityRatio > 0 && cardinalityRatio <= 100, s"Ratio $cardinalityRatio is not valid, please define value between 0 - 100 exclusive !!!")
  private def sampleWithCardinality(progress: Progress): Int = {
    val realCardinality = (progress.total / 100D * cardinalityRatio).toInt
    val sIdx = progress.shuffledIdx
    if (sIdx <= realCardinality) sIdx else sIdx - realCardinality
  }
  private val function: Progress => Int = (progress) => if (cardinalityRatio == 100) progress.shuffledIdx else sampleWithCardinality(progress)
  def sample(progress: Progress): Int = function(progress)
}

case class WeightedEnumeration[T](values: Array[(T, Double)]) extends Distribution[T] {
  private val distribution = Commons.enumeratedDistro(values)
  def sample(progress: Progress): T = distribution.sample
}

case class DistributedInteger(dataPointsCount: Int, dist: IntegerDistribution)(implicit p: Parallelism) extends Distribution[Int] {
  require(dataPointsCount % p.n == 0, s"Please select dataPointsCount so it is divisible by parallelism ${p.n}, $dataPointsCount is not.")
  private def pmf = new IntDistro(dist).getPMF(dataPointsCount / p.n)
  private val distribution = Commons.enumeratedDistro(pmf)
  def sample(progress: Progress): Int = distribution.sample
}

case class DistributedDouble(dataPointsCount: Int, dist: RealDistribution)(implicit p: Parallelism) extends Distribution[Double] {
  require(dataPointsCount % p.n == 0, s"Please select dataPointsCount so it is divisible by parallelism ${p.n}, $dataPointsCount is not.")
  private def pmf = new RealDistro(dist).getPMF(dataPointsCount / p.n)
  private val distribution = Commons.enumeratedDistro(pmf)
  def sample(progress: Progress): Double = distribution.sample
}
