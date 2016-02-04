package gwi.randagen

import org.apache.commons.math3.distribution.UniformIntegerDistribution

/**
  * Distributions are sampled with progress of iteration mostly because of performance reasons, it allows for data distribution
  * without any CPU ticks. Since event can have thousands of fields, it is obvious that each field cannot hold it's own
  * Probability Mass Function. It would lead to 1000 000 of samplings for 1000 of events with 1000 of fields
  * Also you wouldn't be able to control cardinality other than 100%
  */
case class Progress(shuffledIdx: Int, idx: Int, total: Int)

/**
  * EventDef factory is a user supplied definition of DataSet
  * It is a function because it is being created lazily #parallelism times
  * Commons Math is not thread safe hence each thread keeps its own generator instance
  */
trait EventDef {
  def apply(implicit p: Parallelism): List[FieldDef]
}

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

/**
  * FieldDef is a definition of a single field/column
  *
  * @note that it has `count` distribution which specifies into how many fields should this definition be expanded to
  * All fields will be identical with name suffixed with number
  * Each field will have it's own distribution - that's why Distribution and Mapper are passed as functions
  */
object FieldDef {
  def apply[I,O](name: String, dist: Distribution[I], mapper: Mapper[I,O], quantity: FieldQuantity = ConstantQuantity(1)): FieldDef = { (progress, format) =>
    quantity.names(name, progress)
      .map { n =>
        format match {
          case _: JsonFormat =>
            val value = mapper.apply(dist.sample(progress))
            def formatValue =
              if (value.isInstanceOf[String])
                s"""\"$value\""""
              else
                value
            s"""\"$n\": $formatValue"""
          case _: DsvFormat =>
            mapper.apply(dist.sample(progress)).toString
          case x =>
            throw new IllegalArgumentException(s"Format $x not supported, please use 'json' or 'dsv' !!!")
        }
      }
  }
}