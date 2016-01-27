package gwi.randagen

import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import gwi.randagen.ProbabilityDistribution.RealDistributionPimp

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try, Random}

case class Progress(shuffledIdx: Int, idx: Int, total: Int)

/**
  * @note that ValueGenerator implementations should be stateless and idempotent.
  *       For instance a single field can be generated 1000 times (1000 columns) sharing a single generator
  *       So that calling generator 1000 times for a single row shouldn't affect results at all
  */
sealed trait ValueGenerator {
  def gen(progress: Progress): String
}

case class StrictGenerator(value: String) extends ValueGenerator {
  def gen(progress: Progress): String = value
}

case class RandomDoubleGenerator(precision: Int) extends ValueGenerator {
  def randomDouble = BigDecimal(Random.nextDouble()).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toString
  def gen(progress: Progress): String = randomDouble
}

case class RandomSelectGenerator(values: Array[String]) extends ValueGenerator {
  def gen(progress: Progress): String = values(Random.nextInt(values.length))
}

case class CardinalityUuidGenerator(ratio: Int) extends ValueGenerator {
  require(ratio > 0 && ratio < 100, s"Ratio $ratio is not valid, please define value between 0 - 100 exclusive !!!")
  var realCardinality: Long = 0 // strictly for performance reasons, just lazy initialization, race conditions wouldn't matter here
  def uuidFrom(seed: Long) = UUID.nameUUIDFromBytes(seed.toString.getBytes).toString
  def gen(progress: Progress): String = {
    if (realCardinality == 0)
      realCardinality = BigDecimal(progress.total / 100D * ratio).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLongExact
    val sIdx = progress.shuffledIdx
    if (sIdx <= realCardinality) uuidFrom(sIdx) else uuidFrom(sIdx-realCardinality)
  }
}

case class TimeGenerator(pattern: String, unit: String, start: String) extends ValueGenerator {
  import java.time.temporal.ChronoUnit._
  val formatter = DateTimeFormatter.ofPattern(pattern)
  val startTime = LocalDateTime.from(formatter.parse(start))
  val availableUnits = ListMap(
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
  val chronoUnit = availableUnits.getOrElse(unit, throw new IllegalArgumentException(s"Time unit $unit is not supported, use ${availableUnits.keys.mkString(",")} !"))
  override def gen(progress: Progress): String = startTime.plus(progress.idx, chronoUnit).format(formatter)
}

case class WeightedSelectGenerator(values: Array[(String, Double)]) extends ValueGenerator {
  val distribution = ProbabilityDistribution.enumeratedDistro(values)
  def gen(progress: Progress): String = distribution.sample
}

case class ProbabilityDistributionGenerator(absoluteCardinality: Int, className: String, args: Seq[Double]) extends ValueGenerator {
  def pmf = ProbabilityDistribution(className, args).getPMF(absoluteCardinality)
  val distribution = ProbabilityDistribution.enumeratedDistro(pmf)
  def gen(progress: Progress): String = distribution.sample.toString
}

case class FieldDef(name: String, valueType: String, count: Int, valueGen: ValueGenerator)
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
      FieldDef("time",     StringType,  1,    TimeGenerator("yyyy-MM-dd'T'HH:mm:ss.SSS", "Millis", "2015-01-01T00:00:00.000")),
      FieldDef("gwid",     StringType,  1,    CardinalityUuidGenerator(50)),
      FieldDef("country",  StringType,  1,    RandomSelectGenerator(countries)),
      FieldDef("purchase", StringType,  1,    WeightedSelectGenerator(purchase)),
      FieldDef("section",  StringType,  1,    ProbabilityDistributionGenerator(10000, "org.apache.commons.math3.distribution.NormalDistribution", Seq(0D, 0.2))),
      FieldDef("active",   BooleanType, 1,    StrictGenerator("true")),
      FieldDef("kv",       StringType,  3,    ProbabilityDistributionGenerator(10, "org.apache.commons.math3.distribution.NormalDistribution", Seq(0D, 0.2))),
      FieldDef("price",    IntType,     1,    RandomDoubleGenerator(2))
    )
  }
}