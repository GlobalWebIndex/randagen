package gwi.randagen

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

case class TimeMapper(pattern: String, unit: ChronoUnit, start: LocalDateTime = LocalDateTime.now()) extends Mapper[Int,String] {
  private val formatter = DateTimeFormatter.ofPattern(pattern)
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
