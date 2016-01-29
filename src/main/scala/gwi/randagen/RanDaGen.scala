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

  def generate(batchSize: Int, maxBatchByteSize: Int, eventCount: Int, producer: EventProducer, consumers: List[EventConsumer]): Future[List[BatchRes]] = {
    IntShuffler.shuffledIterator(0, eventCount)
      .zipWithIndex
      .foldLeft(Future.successful(List.empty[BatchRes]), 0, new ArrayBuffer[Array[Byte]](batchSize)) { case ((flushingFuture, byteSize, acc), (shuffledIdx, idx)) =>
        def pullEvent = producer.produce(Progress(shuffledIdx, idx, eventCount))
        def pushEvents(loadSize: Int, load: Iterable[Array[Byte]]) =
          Future.fold(List(flushingFuture, Future.sequence(consumers.map(_.consume(BatchReq(s"$idx.${producer.extension}", loadSize, load))))))(List.empty[BatchRes]) {
            case (oldRes, newRes) => newRes ++ oldRes
          }
        def tryBackPressure =
          if (!flushingFuture.isCompleted) { // note that IO should be mostly faster than data generation - if not, this will slow data generation down
            println("Io doesn't keep up with data generation, backpressuring !")
            Await.ready(flushingFuture, 1.minute)
          }

        if (eventCount-1 == idx) { // last event
          val bytes = pullEvent.getBytes
          acc.append(bytes)
          (pushEvents(byteSize+bytes.length, acc), 0, ArrayBuffer.empty)
        } else if (byteSize > maxBatchByteSize || acc.length == batchSize) { // batch is ready
          tryBackPressure
          val bytes = pullEvent.getBytes
          (pushEvents(byteSize, acc), bytes.length, new ArrayBuffer(batchSize).+=(bytes))
        } else {
          val bytes = pullEvent.getBytes
          (flushingFuture, byteSize+bytes.length, acc.+=(bytes))
        }
      }._1
  }
}