package gwi

package object randagen {

  /**
   * EventProducer is given Event Definition comprising of field definitions
   * It is basically a specification of how events should be generated
   */

  type FieldDef = Progress => String
  type EventDef = List[FieldDef]
}
