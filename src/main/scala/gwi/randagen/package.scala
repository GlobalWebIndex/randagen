package gwi

package object randagen {

  /**
    * It is basically a specification of how events should be generated
    * Definition of the field value generation process, Progress is needed for value generation, Format for serialization
    * It is lazy because :
    *   1) each event can have varying count of fields (eg. FieldDef representing key-value query parameters can yield 1 - 1000 fields)
    *   2) it is constructed for each thread because Commons Math distributions are not thread-safe
    */
  type FieldDef = (Progress, Format) => Iterator[String]

  /**
    * Mappers are functions that take Distribution sample (usually Int and Double) as input
    * and generate some meaningful value as output (uuid, timeStamp, rounded double for pricing, etc.)
    */
  type Mapper[I,O] = (I => O)
}
