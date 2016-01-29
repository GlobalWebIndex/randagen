package gwi.randagen

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
  * Mappers are functions that take Distribution sample (usually Int and Double) as input
  * and generate some meaningful value as output (uuid, timeStamp, rounded double for pricing, etc.)
  */
trait Mapper[-I,+O] extends ((I) => O) {
  def apply(v: I): O
}

case class TimeMapper(pattern: String, unit: ChronoUnit, start: LocalDateTime = LocalDateTime.now()) extends Mapper[Int,String] {
  val formatter = DateTimeFormatter.ofPattern(pattern)
  def apply(v: Int): String = start.plus(v, unit).format(formatter)
}

class RoundingMapper(precision: Int) extends Mapper[Double,Double] {
  def apply(v: Double): Double = BigDecimal(v).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toDouble
}

class IdentityMapper[I] extends Mapper[I,I] {
  def apply(v: I): I = v
}

class UuidMapper[I] extends Mapper[I,String] {
  def apply(v: I): String = UUID.nameUUIDFromBytes(v.toString.getBytes).toString
}
