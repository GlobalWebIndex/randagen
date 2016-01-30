package gwi

package object randagen {

  /**
   * EventProducer is given Event Definition comprising of field definitions
   * It is basically a specification of how events should be generated
   */

  type FieldDef = Progress => String
  type EventDef = List[FieldDef]

  /**
    * Mappers are functions that take Distribution sample (usually Int and Double) as input
    * and generate some meaningful value as output (uuid, timeStamp, rounded double for pricing, etc.)
    */
  type Mapper[I,O] = (I => O)

  /**
    * Event generator is a user supplied definition of how DataSet is generated
    * It is a function because it created #parallelism times
    * Commons Math is not thread safe hence each thread keeps its own generator instance
    */
  type EventGeneratorFactory = Parallelism => EventGenerator
}
