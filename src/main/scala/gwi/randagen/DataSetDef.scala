package gwi.randagen

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
  * @note that it has `count` distribution which specifies into how many fields should this definition be expanded to
  * All fields will be identical with name suffixed with number
  * Each field will have it's own distribution - that's why Distribution and Mapper are passed as functions
  */
object FieldDef {
  def apply[I,O](name: String, countDist: Distribution[Int], dist: Distribution[I], mapper: Mapper[I,O]): FieldDef = { (progress, format) =>
    val count = countDist.sample(progress)
    val names = if (count == 1) Iterator(name) else Iterator.range(1, count).map(idx => s"${name}_$idx")
    names.map { n =>
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

trait Format {
  def extension: String
  def eolChar: String
}
trait DsvFormat extends Format {
  def delimiter: String
  val eolChar = "\n"
}
trait JsonFormat extends Format {
  val extension = "json"
}
case object DefaultJsonFormat extends JsonFormat {
  val eolChar = ""
}
case object PrettyJsonFormat extends JsonFormat {
  val eolChar = "\n"
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
  def generate(p: Progress): String = mkString(eventDef.flatMap(_(p, format)))
  def eventDef: EventDef
}
case class DsvEventGenerator(val eventDef: EventDef, val format: DsvFormat) extends EventGenerator {
  def mkString(xs: Iterable[String]) = xs.mkString("", format.delimiter, format.eolChar)
}
case class JsonEventGenerator(val eventDef: EventDef, val format: JsonFormat = DefaultJsonFormat) extends EventGenerator {
  def mkString(xs: Iterable[String]) = xs.mkString("{", ", ", s"}${format.eolChar}")
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
    case "pretty-json" => JsonEventGenerator(eventDef, PrettyJsonFormat)
    case "json" => JsonEventGenerator(eventDef)
    case "csv" => DsvEventGenerator(eventDef, CsvFormat)
    case "tsv" => DsvEventGenerator(eventDef, TsvFormat)
  }

}