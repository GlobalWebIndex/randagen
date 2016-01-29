package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformRealDistribution}

/**
  * Distributions are sampled with progress of iteration mostly because of performance reasons, it allows for data distribution
  * without any CPU ticks. Since event can have thousands of fields, it is obvious that each field cannot hold it's own
  * Probability Mass Function. It would lead to 1000 000 of samplings for 1000 of events with 1000 of fields
  * Also you wouldn't be able to control cardinality other than 100%
  */
case class Progress(shuffledIdx: Int, idx: Int, total: Int)

/**
  * FieldDef is a definition of a single field/column
  *
  * @note that it has `count` property which specifies into how many fields should this definition be expanded to
  * All fields will be identical with name suffixed with number, a value will be generated for each field extra
  */
object FieldDef {
  def apply[I,O](name: String, format: String, count: Int, dist: () => Distribution[I], mapper: () => Mapper[I,O]): IndexedSeq[FieldDef] = {
    def json(name: String, dist: Distribution[I], mapper: Mapper[I,O])(progress: Progress): String = {
      val value = mapper.apply(dist.sample(progress))
      def formatValue =
        if (value.isInstanceOf[String])
          s"""\"$value\""""
        else
          value
      s"""\"$name\": $formatValue"""
    }

    def dsv(name: String, dist: Distribution[I], mapper: Mapper[I,O])(progress: Progress): String =
      mapper.apply(dist.sample(progress)).toString

    def formatBy(fn: (String, Distribution[I], Mapper[I, O]) => FieldDef) = {
      if (count > 1)
        (0 until count).map (idx => fn(s"${name}_$idx", dist(), mapper()))
      else
        IndexedSeq(fn(name, dist(), mapper()))
    }

    format match {
      case "json" => formatBy(json)
      case "dsv" => formatBy(dsv)
      case x => throw new IllegalArgumentException(s"Format $x not supported, please use 'json' or 'dsv' !!!")
    }
  }
}

trait EventProducer {
  def extension: String
  def produce(progress: Progress): String
  def fieldDefs: EventDef
}
trait DsvEventProducer extends EventProducer {
  def delimiter: String
  def produce(progress: Progress): String = fieldDefs.map(_(progress)).mkString("", delimiter, "\n")
}
case class JsonEventProducer(val fieldDefs: EventDef) extends EventProducer {
  def extension: String = "json"
  def produce(progress: Progress): String = fieldDefs.map(_(progress)).mkString("{", ", ", "}")
}
case class CsvEventProducer(val fieldDefs: EventDef) extends DsvEventProducer {
  val delimiter = ","
  val extension = "csv"
}
case class TsvEventProducer(val fieldDefs: EventDef) extends DsvEventProducer {
  val delimiter = "\t"
  val extension = "csv"
}

object EventProducer {

  def get(name: String, format: String): EventProducer = Map(
    "sample" -> sample(format)
  ).get(name)
    .map[EventProducer] {
      case fieldDef if format == "json" => JsonEventProducer(fieldDef)
      case fieldDef if format == "csv"  => CsvEventProducer(fieldDef)
      case fieldDef if format == "tsv"  => TsvEventProducer(fieldDef)
    }.getOrElse(throw new IllegalArgumentException(s"DataSet definition $name does not exist !!!"))

  private def sample(format: String): EventDef = {
    def purchase = Array("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)
    def countries = {
      val list = List(
        "bra","nzl","bel","bgr","idn","egy","tur","nor","pol","jpn","esp","irl","cze","dnk","che","nld",
        "ita","rus","pri","deu","eur","pry","usa","dom","gtm","ury","col","fra","isr","arg","mex","gbr"
      )
      list.zip(list.foldLeft(List(0.1)) { case (acc, _) => (acc.head * 2) :: acc }).toArray
    }

    IndexedSeq(
      FieldDef("time", format, 1,
        () => Linear,
        () => TimeMapper("yyyy-MM-dd'T'HH:mm:ss.SSS", ChronoUnit.MILLIS, LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0))
      ),
      FieldDef("gwid", format, 1,
        () => Random(50),
        () => new UuidMapper[Int]
      ),
      FieldDef("country", format, 1,
        () => WeightedEnumeration[String](countries),
        () => new IdentityMapper[String]
      ),
      FieldDef("purchase", format, 1,
        () => WeightedEnumeration[String](purchase),
        () => new IdentityMapper[String]
      ),
      FieldDef("section", format, 1,
        () => DistributedDouble(10000, new NormalDistribution(0D, 0.2)),
        () => new IdentityMapper[Double]
      ),
      FieldDef("kv", format, 10,
        () => DistributedDouble(10, new NormalDistribution(0D, 0.2)),
        () => new IdentityMapper[Double]
      ),
      FieldDef("price", format, 1,
        () => DistributedDouble(100, new UniformRealDistribution(1, 1000)),
        () => new RoundingMapper(2)
      )
    ).flatten.toList
  }
}