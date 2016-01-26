package gwi.randagen

sealed trait EventProducer {
  def extension: String
  def produce(progress: Progress): String
}

case class JsonEventProducer(fieldDefs: DataSetDef) extends EventProducer {
  import JsonEventProducer._
  def extension: String = "json"
  def produce(progress: Progress): String =
    fieldDefs.map { fieldDef =>
      def generateValue = fieldDef.valueGen.gen(progress)
      def formatValue =
        fieldDef.valueType match {
          case StringType => s"""\"$generateValue\""""
          case IntType => generateValue
          case BooleanType => generateValue
          case x => throw new IllegalArgumentException(s"Supported field valueTypes are only : 'String' and 'Int'. Not $x !")
        }
      s"""\"${fieldDef.name}\": $formatValue"""
    }.mkString("{", ", ", "}")
}

object JsonEventProducer {
  val StringType = "String"
  val IntType = "Int"
  val BooleanType = "Boolean"
}

case class DsvEventProducer(val extension: String, fieldDefs: DataSetDef) extends EventProducer {
  import DsvEventProducer._
  lazy val sep: String = extension match {
    case CsvType => ","
    case TsvType => "\t"
    case x => throw new IllegalArgumentException(s"Extension $x not supported, use space or tab as a delimiter !")
  }
  def produce(progress: Progress): String = fieldDefs.map(_.valueGen.gen(progress)).mkString("", sep, "\n")
}

object DsvEventProducer {
  val CsvType = "csv"
  val TsvType = "tsv"
}