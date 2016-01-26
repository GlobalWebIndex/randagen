package gwi.randagen

sealed trait EventProducer {
  def extension: String
  def produce(progress: Progress): String
}

case class JsonEventProducer(fieldDefs: DataSetDef) extends EventProducer {
  def extension: String = "json"
  def produce(progress: Progress): String =
    fieldDefs.map { fieldDef =>
      def generateValue = fieldDef.valueGen.gen(progress)
      def formatValue =
        fieldDef.valueType match {
          case "String" => s"""\"$generateValue\""""
          case "Int" => generateValue
          case x => throw new IllegalArgumentException(s"Supported field valueTypes are only : 'String' and 'Int'. Not $x !")
        }
      s"""\"${fieldDef.name}\": $formatValue"""
    }.mkString("{", ", ", "}")
}

case class DsvEventProducer(val extension: String, fieldDefs: DataSetDef) extends EventProducer {
  lazy val sep: String = extension match {
    case "csv" => ","
    case "tsv" => "\t"
    case x => throw new IllegalArgumentException(s"Extension $x not supported, use space or tab as a delimiter !")
  }
  def produce(progress: Progress): String = fieldDefs.map(_.valueGen.gen(progress)).mkString("", sep, "\n")
}