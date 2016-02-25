package gwi.randagen

/**
  * Distributions are sampled with progress of iteration mostly because of performance reasons, it allows for data distribution
  * without any CPU ticks. Since event can have thousands of fields, it is obvious that each field cannot hold it's own
  * Probability Mass Function. It would lead to 1000 000 of samplings for 1000 of events with 1000 of fields
  * Also you wouldn't be able to control cardinality other than 100%
  */
case class Progress(shuffledIdx: Int, idx: Int, total: Int)

/**
  * Definition of the field value generation process, Progress is needed for value generation, Format for serialization.
  *   1) each event can have varying count of fields (eg. FieldDef representing key-value query parameters can yield 1 - 1000 fields)
  *   2) it is constructed for each thread because Commons Math distributions are not thread-safe
  *
  * @param dist determines how values are distributed in column
  * @param valueDef is a definition of what a Distribution sample (usually Int and Double) should be converted into (timestamp, uuid)
  * @param quantity specifies into how many fields should this definition be expanded to
  *                 All fields will be identical with name suffixed with number
  *                 Each field will have it's own distribution - that's why Distribution and Mapper are passed as functions
  */
case class FieldDef[I,O](name: String, description: String, dist: Distribution[I], valueDef: FieldValueDef[I,O], quantity: FieldQuantity = ConstantQuantity(1)) {
  def generate(progress: Progress, format: Format):  Iterator[String] = {
    quantity.names(name, progress)
      .map { n =>
        format match {
          case JsonFormat =>
            val value = valueDef.apply(dist.sample(progress))
            def formatValue =
              if (value.isInstanceOf[String])
                s"""\"$value\""""
              else
                value
            s"""\"$n\": $formatValue"""
          case _: DsvFormat =>
            valueDef.apply(dist.sample(progress)).toString
          case x =>
            throw new IllegalArgumentException(s"Format $x not supported, please use 'json' or 'dsv' !!!")
        }
      }
  }
}

/**
  * EventDef factory is a user supplied definition of DataSet
  * It is a function because it is being created lazily #parallelism times
  * Commons Math is not thread safe hence each thread keeps its own generator instance
  */
trait EventDefFactory {
  def apply(implicit p: Parallelism): EventDef
}

case class EventDef(fieldDefs: List[FieldDef[_,_]], pathDefOpt: Option[PathDef])
