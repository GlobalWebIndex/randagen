package gwi.randagen

import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.SplittableRandom

import gwi.randagen.Commons.RealDistributionPimp

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

case class Progress(shuffledIdx: Int, idx: Int, total: Int)

/**
  * @note that ValueGenerator implementations should be stateless and idempotent.
  *       For instance a single field can be generated 1000 times (1000 columns) sharing a single generator
  *       So that calling generator 1000 times for a single row shouldn't affect results at all
  */
sealed trait Value {
  def gen(progress: Progress): String
}

case class Constant(value: String) extends Value {
  def gen(progress: Progress): String = value
}

case class RandomDouble(precision: Int) extends Value {
  val rd = new SplittableRandom()
  def randomDouble = BigDecimal(rd.nextDouble()).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toString
  def gen(progress: Progress): String = randomDouble
}

case class RandomSelect(values: Array[String]) extends Value {
  val rd = new SplittableRandom()
  def gen(progress: Progress): String = values(rd.nextInt(values.length))
}

case class Cardinality(ratio: Int) extends Value {
  require(ratio > 0 && ratio < 100, s"Ratio $ratio is not valid, please define value between 0 - 100 exclusive !!!")
  def gen(progress: Progress): String = {
    val realCardinality = (progress.total / 100D * ratio).toInt
    val sIdx = progress.shuffledIdx
    if (sIdx <= realCardinality) sIdx.toString else (sIdx-realCardinality).toString
  }
}

case class TimeStamp(pattern: String, unit: String, start: String) extends Value {
  import java.time.temporal.ChronoUnit._
  def availableUnits = ListMap(
    "Nanos"    -> NANOS,
    "Micros"   -> MICROS,
    "Millis"   -> MILLIS,
    "Seconds"  -> SECONDS,
    "Minutes"  -> MINUTES,
    "Hours"    -> HOURS,
    "Days"     -> DAYS,
    "Weeks"    -> WEEKS,
    "Months"   -> MONTHS,
    "Years"    -> YEARS
  )
  val formatter = DateTimeFormatter.ofPattern(pattern)
  val startTime = LocalDateTime.from(formatter.parse(start))
  val chronoUnit = availableUnits.getOrElse(unit, throw new IllegalArgumentException(s"Time unit $unit is not supported, use ${availableUnits.keys.mkString(",")} !"))
  def gen(progress: Progress): String = startTime.plus(progress.idx, chronoUnit).format(formatter)
}

case class WeightedSelect(values: Array[(String, Double)]) extends Value {
  val distribution = Commons.enumeratedDistro(values)
  def gen(progress: Progress): String = distribution.sample
}

case class ProbabilityDistribution(dataPointsCount: Int, className: String, args: Seq[Double]) extends Value {
  def pmf = Commons(className, args).getPMF(dataPointsCount)
  val distribution = Commons.enumeratedDistro(pmf)
  def gen(progress: Progress): String = distribution.sample.toString
}

case class FieldDef(name: String, valueType: String, count: Int, value: Value)
object DataSetDef {
  import upickle.default._
  import JsonEventProducer._

  def serialize(dataSetDef: DataSetDef, indent: Int): String = write[DataSetDef](dataSetDef, indent)

  def deserialize(jsonPath: Path): DataSetDef = {
    require(jsonPath.toFile.exists(), s"File $jsonPath does not exists!")
    val json = new String(Files.readAllBytes(jsonPath))
    Try(read[DataSetDef](json)) match {
      case Success(dataSetDef) =>
        dataSetDef
      case Failure(ex) =>
        throw new IllegalArgumentException(s"Please fix your data-set definition, example :\n" + serialize(sampleDataSetDef, 4), ex)
    }
  }

  def sampleDataSetDef: DataSetDef = {
    def countries = Array("bra","nzl","bel")

    def purchase = Array("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)

    List(
      FieldDef("time",     StringType,  1,    TimeStamp("yyyy-MM-dd'T'HH:mm:ss.SSS", "Millis", "2015-01-01T00:00:00.000")),
      FieldDef("gwid",     UuidType,    1,    Cardinality(50)),
      FieldDef("country",  StringType,  1,    RandomSelect(countries)),
      FieldDef("purchase", StringType,  1,    WeightedSelect(purchase)),
      FieldDef("section",  StringType,  1,    ProbabilityDistribution(10000, "org.apache.commons.math3.distribution.NormalDistribution", Seq(0D, 0.2))),
      FieldDef("active",   BooleanType, 1,    Constant("true")),
      FieldDef("kv",       StringType,  3,    ProbabilityDistribution(10, "org.apache.commons.math3.distribution.NormalDistribution", Seq(0D, 0.2))),
      FieldDef("price",    IntType,     1,    RandomDouble(2))
    )
  }
}