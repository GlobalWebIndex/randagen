package gwi.randagen

import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import gwi.randagen.CommonsDistribution.RealDistributionPimp

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try, Random}

case class Progress(shuffledIdx: Int, idx: Int, total: Int)

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
  def uuidFrom(seed: Int) = UUID.nameUUIDFromBytes(seed.toString.getBytes).toString
  def gen(progress: Progress): String = {
    val sIdx = progress.shuffledIdx
    val realCardinality = BigDecimal(progress.total / 100D * ratio).setScale(0, BigDecimal.RoundingMode.HALF_UP).toIntExact
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

case class WeightedSelectGenerator(values: Seq[(String, Double)]) extends ValueGenerator {
  val sortedValues = values.sortBy(_._2)(Ordering[Double].reverse)
  val distribution = EnumeratedDistro(sortedValues)
  def gen(progress: Progress): String = distribution.sample
}

case class ProbabilityDistributionGenerator(absoluteCardinality: Int, className: String, arg1: Double, arg2: Double) extends ValueGenerator {
  val probDistFunction = CommonsDistribution(className, arg1, arg2).getProbabilityDistribution(absoluteCardinality)
  val distribution = EnumeratedDistro(probDistFunction)
  def gen(progress: Progress): String = distribution.sample.toString
}

case class FieldDef(name: String, valueType: String, valueGen: ValueGenerator)
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
    def countries = Seq("bra","nzl","bel")

    def sections = Seq("home","sport","news","finance")

    def purchase = Seq("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)

    List(
      FieldDef("time",      StringType,   TimeGenerator("yyyy-MM-dd'T'HH:mm:ss.SSS", "Millis", "2015-01-01T00:00:00.000")),
      FieldDef("gwid",      StringType,   CardinalityUuidGenerator(50)),
      FieldDef("country",   StringType,   RandomSelectGenerator(countries.toArray)),
      FieldDef("purchase",  StringType,   WeightedSelectGenerator(purchase)),
      FieldDef("section",   StringType,   ProbabilityDistributionGenerator(10000, "org.apache.commons.math3.distribution.NormalDistribution", 0D, 0.2)),
      FieldDef("active",    BooleanType,  StrictGenerator("true")),
      FieldDef("price",     IntType,      RandomDoubleGenerator(2))
    )
  }
}