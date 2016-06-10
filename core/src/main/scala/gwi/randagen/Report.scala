package gwi.randagen

import java.text.{DecimalFormatSymbols, DecimalFormat}

import scala.math.BigDecimal.RoundingMode._

case class Report(eventCount: Int, producerResponses: List[ProducerResponse], consumerResponses: List[ConsumerResponse]) {
  private val formatter = {
    val symbols = DecimalFormatSymbols.getInstance()
    symbols.setGroupingSeparator(' ')
    new DecimalFormat("#,###", symbols)
  }
  override def toString = {
    def scale(value: Double) = BigDecimal(value).setScale(2, HALF_UP)
    def zipByThread(xs: Iterable[Long]) = xs.zipWithIndex.map(t => s"${t._2} thread : ${formatter.format(t._1)} ms").mkString("\n", "\n", "")
    val slowestProducerTook = producerResponses.map(_.producersTook.toMillis).sorted.last

    lazy val generators = zipByThread(producerResponses.map(_.generatorsTook.toMillis))
    lazy val production = zipByThread(producerResponses.map(_.producersTook.toMillis))
    lazy val persistence = formatter.format(consumerResponses.map(_.took).sum)
    lazy val dataSize = scale(consumerResponses.map(_.byteSize.toLong).sum / (1000 * 1000D)).toDouble
    lazy val dataThroughPut = scale(dataSize / slowestProducerTook * 1000D)
    lazy val eventThroughPut = scale(eventCount / (slowestProducerTook/1000D))
    s"""
       |event count : ${formatter.format(eventCount)}
       |event generator creation took : $generators
       |data production took : $production
       |persistence by consumer took : $persistence ms
       |number of batches/files : ${consumerResponses.size}
       |data size : $dataSize MB
       |data throughput : $dataThroughPut MB/s
       |event throughput : $eventThroughPut events/s
    """.stripMargin
  }
}
