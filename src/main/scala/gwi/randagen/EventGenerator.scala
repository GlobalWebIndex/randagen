package gwi.randagen


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
  protected[this] def mkString(xs: Iterable[String]): String
  protected[randagen] def format: Format
  protected[randagen] def generate(eventDef: List[FieldDef], p: Progress): String = mkString(eventDef.flatMap(_(p, format)))
}
case class DsvEventGenerator(val format: DsvFormat) extends EventGenerator {
  def mkString(xs: Iterable[String]) = xs.mkString("", format.delimiter, format.eolChar)
}
case class JsonEventGenerator(val format: JsonFormat = DefaultJsonFormat) extends EventGenerator {
  def mkString(xs: Iterable[String]) = xs.mkString("{", ", ", s"}${format.eolChar}")
}

object EventGenerator {

  def apply(format: String) = format match {
    case "pretty-json" => JsonEventGenerator(PrettyJsonFormat)
    case "json" => JsonEventGenerator(DefaultJsonFormat)
    case "csv" => DsvEventGenerator(CsvFormat)
    case "tsv" => DsvEventGenerator(TsvFormat)
  }

}
