package gwi.randagen

import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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

case class CardinalityUuidGenerator(rate: Int) extends ValueGenerator {
  def uuidFrom(seed: Int) = UUID.nameUUIDFromBytes(seed.toString.getBytes).toString
  def gen(progress: Progress): String = {
    val sIdx = progress.shuffledIdx
    val realCardinality = (progress.total / 100D * rate).toInt
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

    def purchase = Seq("micro","small","medium","large")

    List(
      FieldDef("time",      StringType,   TimeGenerator("yyyy-MM-dd'T'HH:mm:ss.SSS", "Millis", "2015-01-01T00:00:00.000")),
      FieldDef("gwid",      StringType,   CardinalityUuidGenerator(50)),
      FieldDef("country",   StringType,   RandomSelectGenerator(countries.toArray)),
      FieldDef("purchase",  StringType,   RandomSelectGenerator(purchase.toArray)),
      FieldDef("section",   StringType,   RandomSelectGenerator(sections.toArray)),
      FieldDef("active",    BooleanType,  StrictGenerator("true")),
      FieldDef("price",     IntType,      RandomDoubleGenerator(2))
    )
  }
}