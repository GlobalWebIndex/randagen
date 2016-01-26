package gwi.randagen

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * High performance generator of random data.
  * It can persist half a billion events with 30GB of data in 10 minutes using just 6GB of Heap.
  * It is able to generate randomly distributed data with predefined cardinality which is the main speed and data volume bottleneck
  */
object RanDaGen {

  /**
    * @note that this method exists merely because it is the only way to be sure that SDK doesn't perform Boxing of primitives
    */
  def flattenArray(xs: Iterable[Array[Byte]]): Array[Byte] =
    xs.foldLeft((0, new Array[Byte](xs.iterator.map(_.length).sum))) { case ((arrIdx, targetArr), event) =>
      val size = event.length
      System.arraycopy(event, 0, targetArr, arrIdx, size)
      size+arrIdx -> targetArr
    }._2

  def generate(batchSize: Int, eventCount: Int, producer: EventProducer, consumers: List[EventConsumer]): Future[List[BatchRes]] = {
    IntShuffler.shuffledIterator(0, eventCount)
      .zipWithIndex
      .foldLeft(Future.successful(List.empty[BatchRes]), new ArrayBuffer[Array[Byte]](batchSize)) { case ((flushingFuture, acc), (shuffledIdx, idx)) =>
        def pullEvent = producer.produce(Progress(shuffledIdx, idx, eventCount))
        def pushEvents = Future.fold(List(flushingFuture, Future.sequence(consumers.map(_.consume(BatchReq(s"$idx.${producer.extension}", flattenArray(acc)))))))(List.empty[BatchRes]) {
            case (oldRes, newRes) => newRes ++ oldRes
          }
        def tryBackPressure =
          if (!flushingFuture.isCompleted) { // note that IO should be mostly faster than data generation - if not, this will slow data generation down
            println("Io doesn't keep up with data generation, backpressuring !")
            Await.ready(flushingFuture, 1.minute)
          }

        if (eventCount-1 == idx) {
          acc.append(pullEvent.getBytes)
          pushEvents -> ArrayBuffer.empty
        } else if (acc.length == batchSize) {
          tryBackPressure
          pushEvents -> new ArrayBuffer(batchSize).+=(pullEvent.getBytes)
        } else {
          flushingFuture -> acc.+=(pullEvent.getBytes)
        }
      }._1
  }
}