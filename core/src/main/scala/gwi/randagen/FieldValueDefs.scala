package gwi.randagen

import java.util.UUID

/**
  * FieldValueDefs are functions that take Distribution sample (usually Int and Double) as input
  * and generate some meaningful value as output (uuid, timeStamp, rounded double for pricing, etc.)
  */
trait FieldValueDef[I,O] extends (I => O)

case class TimeValueDef(clock: Clock) extends FieldValueDef[Int,String] {
  def apply(v: Int): String = clock.rewindForwardBy(v)
}

class RoundingValueDef(precision: Int) extends FieldValueDef[Double,Double] {
  def apply(v: Double): Double = BigDecimal(v).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toDouble
}

class IdentityValueDef[I] extends FieldValueDef[I,I] {
  def apply(v: I): I = v
}

class UuidValueDef[I] extends FieldValueDef[I,String] {
  def apply(v: I): String = UUID.nameUUIDFromBytes(v.toString.getBytes).toString
}