package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{UniformIntegerDistribution, NormalDistribution, UniformRealDistribution}

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
  * All fields will be identical with name suffixed with number
  * Each field will have it's own distribution - that's why Distribution and Mapper are passed as functions
  */
object FieldDef {
  def apply[I,O](name: String, countDist: Distribution[Int], dist: () => Distribution[I], mapper: () => Mapper[I,O]): FieldDef = {
    def format(name: String, p: Progress, dist: Distribution[I], mapper: Mapper[I,O])(f: Format): String = f match {
      case JsonFormat =>
        val value = mapper.apply(dist.sample(p))
        def formatValue =
          if (value.isInstanceOf[String])
            s"""\"$value\""""
          else
            value
        s"""\"$name\": $formatValue"""
      case _: DsvFormat =>
        mapper.apply(dist.sample(p)).toString
      case x =>
        throw new IllegalArgumentException(s"Format $x not supported, please use 'json' or 'dsv' !!!")
    }

    p => {
      val c = countDist.sample(p)
      if (c > 1)
        Iterator.range(1, c).map (idx => format(s"${name}_$idx", p, dist(), mapper())_)
      else
        Iterator(format(name, p, dist(), mapper())_)
    }
  }
}

trait Format {
  def extension: String
}
trait DsvFormat extends Format {
  def delimiter: String
}
case object JsonFormat extends Format {
  val extension = "json"
}
case object CsvFormat extends DsvFormat {
  val extension = "csv"
  val delimiter = ","
}
case object TsvFormat extends DsvFormat {
  val extension = "tsv"
  val delimiter = "\t"
}

trait EventGenerator {
  def format: Format
  def mkString(xs: Iterable[String]): String
  def generate(p: Progress): String = mkString(eventDef.flatMap(_(p).map(_(format))))
  def eventDef: EventDef
}
case class DsvEventGenerator(val eventDef: EventDef, val format: DsvFormat) extends EventGenerator {
  def mkString(xs: Iterable[String]) = xs.mkString("", format.delimiter, "\n")
}
case class JsonEventGenerator(val eventDef: EventDef) extends EventGenerator {
  def format = JsonFormat
  def mkString(xs: Iterable[String]) = xs.mkString("{", ", ", "}\n")
}

object EventGenerator {

  def factory(format: String, name: String): EventGeneratorFactory = { p: Parallelism =>
    Map(
      "sample" -> Samples.gwiSampleEventDef(p)
    ).get(name)
      .map(EventGenerator(format, _))
      .getOrElse(throw new IllegalArgumentException(s"DataSet definition $name does not exist !!!"))
  }

  private def apply(format: String, eventDef: EventDef) = format match {
    case "json" => JsonEventGenerator(eventDef)
    case "csv" => DsvEventGenerator(eventDef, CsvFormat)
    case "tsv" => DsvEventGenerator(eventDef, TsvFormat)
  }

}