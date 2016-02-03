package gwi.randagen

import java.text.{DecimalFormatSymbols, DecimalFormat}

import scala.math.BigDecimal.RoundingMode._

case class Report(eventCount: Int, producerResponses: List[ProducerResponse], consumerResponses: List[ConsumerResponse]) {
  val formatter = {
    val symbols = DecimalFormatSymbols.getInstance()
    symbols.setGroupingSeparator(' ')
    new DecimalFormat("#,###", symbols)
  }
  def scale(value: Double) = BigDecimal(value).setScale(2, HALF_UP)
  def zipByThread(xs: Iterable[Long]) = xs.zipWithIndex.map(t => s"${t._2} thread : ${formatter.format(t._1)} ms").mkString("\n", "\n", "")
  override def toString = {
    lazy val generators = zipByThread(producerResponses.map(_.generatorsTook.toMillis))
    lazy val production = zipByThread(producerResponses.map(_.producersTook.toMillis))
    lazy val persistence = formatter.format(consumerResponses.map(_.took).sum)
    lazy val dataSize = scale(consumerResponses.map(_.byteSize.toLong).sum / (1000 * 1000D)).toDouble
    lazy val throughPut = scale(dataSize / producerResponses.map(_.producersTook.toMillis).sorted.last * 1000D)
    s"""
       |event generator creation took : $generators
       |data production took : $production
       |persistence by consumer took : $persistence ms
       |number of batches/files : ${consumerResponses.size}
       |data size : $dataSize MB
       |throughput : $throughPut MB/s
    """.stripMargin
  }
}
