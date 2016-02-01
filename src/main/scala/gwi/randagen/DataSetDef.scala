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
  def apply[I,O](name: String, count: Int, dist: () => Distribution[I], mapper: () => Mapper[I,O]): IndexedSeq[FieldDef] = {
    def format(name: String, dist: Distribution[I], mapper: Mapper[I,O])(format: String, progress: Progress): String = format match {
      case "json" =>
        val value = mapper.apply(dist.sample(progress))
        def formatValue =
          if (value.isInstanceOf[String])
            s"""\"$value\""""
          else
            value
        s"""\"$name\": $formatValue"""
      case dsv if dsv == "csv" || dsv == "tsv" =>
        mapper.apply(dist.sample(progress)).toString
      case x => throw new IllegalArgumentException(s"Format $x not supported, please use 'json' or 'dsv' !!!")

    }

    if (count > 1)
      (0 until count).map (idx => format(s"${name}_$idx", dist(), mapper())_)
    else
      IndexedSeq(format(name, dist(), mapper())_)
  }
}

trait EventGenerator {
  def format: String
  def generate(progress: Progress): String
  def fieldDefs: EventDef
}
trait DsvEventGenerator extends EventGenerator {
  def delimiter: String
  def generate(progress: Progress): String = fieldDefs.map(_(format, progress)).mkString("", delimiter, "\n")
}
case class JsonEventGenerator(val fieldDefs: EventDef) extends EventGenerator {
  def format: String = "json"
  def generate(progress: Progress): String = fieldDefs.map(_(format, progress)).mkString("{", ", ", "}")
}
case class CsvEventGenerator(val fieldDefs: EventDef) extends DsvEventGenerator {
  val delimiter = ","
  val format = "csv"
}
case class TsvEventGenerator(val fieldDefs: EventDef) extends DsvEventGenerator {
  val delimiter = "\t"
  val format = "tsv"
}

object EventGenerator {

  def factory(format: String, name: String): EventGeneratorFactory = { p: Parallelism =>
    Map(
      "sample" -> sampleEventDef(p)
    ).get(name)
      .map(EventGenerator(format, _))
      .getOrElse(throw new IllegalArgumentException(s"DataSet definition $name does not exist !!!"))
  }

  private def apply(format: String, eventDef: EventDef) = format match {
    case "json" => JsonEventGenerator(eventDef)
    case "csv" => CsvEventGenerator(eventDef)
    case "tsv" => TsvEventGenerator(eventDef)
  }

  private def sampleEventDef(p: Parallelism): EventDef = {
    def purchase = Array("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)
    def countries = {
      val list = List(
        "bra","nzl","bel","bgr","idn","egy","tur","nor","pol","jpn","esp","irl","cze","dnk","che","nld",
        "ita","rus","pri","deu","eur","pry","usa","dom","gtm","ury","col","fra","isr","arg","mex","gbr"
      )
      list.zip(list.foldLeft(List(0.1)) { case (acc, _) => (acc.head * 2) :: acc }).toArray
    }

    IndexedSeq(
      FieldDef("time", 1,
        () => Linear,
        () => TimeMapper("yyyy-MM-dd'T'HH:mm:ss.SSS", ChronoUnit.MILLIS, LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0))
      ),
      FieldDef("gwid", 1,
        () => Random(50),
        () => new UuidMapper[Int]
      ),
      FieldDef("country", 1,
        () => WeightedEnumeration[String](countries),
        () => new IdentityMapper[String]
      ),
      FieldDef("section", 1,
        () => DistributedDouble(10000, new NormalDistribution(0D, 0.2))(p),
        () => new IdentityMapper[Double]
      ),
      FieldDef("purchase", 1,
        () => WeightedEnumeration[String](purchase),
        () => new IdentityMapper[String]
      ),
      FieldDef("kv", 1000,
        () => DistributedDouble(12, new NormalDistribution(0D, 0.2))(p),
        () => new IdentityMapper[Double]
      ),
      FieldDef("price", 1,
        () => DistributedDouble(100, new UniformRealDistribution(1, 1000))(p),
        () => new RoundingMapper(2)
      )
    ).flatten.toList
  }
}