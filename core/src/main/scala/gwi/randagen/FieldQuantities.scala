package gwi.randagen

import org.apache.commons.math3.distribution.UniformIntegerDistribution


/**
  * Data Set needs to simulate real world cases like :
  * Impression log with KeyValue query parameters, each of 100 clients having different set of keys
  * For that you need to virtually split events into 100 segments, each with different # of keys and key names
  */
trait FieldQuantity {
  def names(name: String, p: Progress): Iterator[String] = nameFn(dist.sample(p), name)
  protected[this] def dist: Distribution[Int]
  protected[this] def nameFn(n: Int, name: String): Iterator[String]
}

/**
  * You need this one in most cases, just a single field, it is the default field quantity
  */
case class ConstantQuantity(count: Int) extends FieldQuantity {
  val dist = Constant(count)
  def nameFn(n: Int, name: String) = if (count == 1) Iterator(name) else Iterator.range(1, n).map(idx => s"${name}_$idx")
}

/**
  * Real world example :
  * 100 clients with varying number of Keys (up to 1000), will have `maxSegmentCount=100` and `maxFieldCount=1000`
  */
trait DynamicQuantity extends FieldQuantity {
  val dist = DistributedInteger(maxSegmentCount, new UniformIntegerDistribution(1, maxFieldCount))(Parallelism(1))
  def maxSegmentCount: Int // maximum different field sizes being used for event generation
  def maxFieldCount: Int // maximum field count possible
}

/**
  * Key names are randomly shared among segments
  */
case class SharedNamesQuantity(maxSegmentCount: Int, maxFieldCount: Int) extends DynamicQuantity {
  def nameFn(n: Int, name: String) = Iterator.range(1, n).map(idx => s"${name}_$idx")
}

/**
  * Key names are not shared among segments (they are distinct)
  */
case class DistinctNamesQuantity(maxSegmentCount: Int, maxFieldCount: Int) extends DynamicQuantity {
  def nameFn(n: Int, name: String) = {
    val uniqueSegmentStart = n * n
    val uniqueSegmentEnd = uniqueSegmentStart + n
    Iterator.range(uniqueSegmentStart, uniqueSegmentEnd).map(idx => s"${name}_$idx")
  }
}

/** If existing Quantities don't cover your edge cases, use CustomQuantity */
case class CustomQuantity(dist: Distribution[Int])(fn: (Int, String) => Iterator[String]) extends FieldQuantity {
  def nameFn(n: Int, name: String) = fn(n, name)
}