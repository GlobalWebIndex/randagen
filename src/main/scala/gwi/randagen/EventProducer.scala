package gwi.randagen

sealed trait EventProducer {
  def extension: String
  def produce(progress: Progress): String
}

object EventProducer {
  /**
    * FieldDefs has `count` property which specifies into how many fields should this definition be expanded
    * All fields will be identical with name suffixed with number, a value will be generated for each field extra
    */
  private def unfold(fieldDefs: DataSetDef): DataSetDef = {
    fieldDefs.flatMap {
      case FieldDef(name, valType, count, gen) if count > 1 =>
        (1 to count).map( idx => FieldDef(s"${name}_$idx", valType, count, gen))
      case fieldDef if fieldDef.count == 1 =>
        fieldDef :: Nil
      case fieldDef =>
        throw new IllegalArgumentException(s"Field $fieldDefs is not valid, count must be greater than 0 !!!")
    }
  }

  def ofJson(fieldDefs: DataSetDef): JsonEventProducer = JsonEventProducer(unfold(fieldDefs))
  def ofDsv(extension: String, fieldDefs: DataSetDef): DsvEventProducer = DsvEventProducer(extension, unfold(fieldDefs))
}

case class JsonEventProducer private[randagen](fieldDefs: DataSetDef) extends EventProducer {
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

case class DsvEventProducer private[randagen](val extension: String, fieldDefs: DataSetDef) extends EventProducer {
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