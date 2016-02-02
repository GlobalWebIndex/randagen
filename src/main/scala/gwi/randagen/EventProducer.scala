package gwi.randagen

import com.typesafe.scalalogging.LazyLogging
import gwi.randagen.ArrayUtils.ArrayPimp

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Parallelism information needs to be used mainly by Distributions with explicitly declared number of data-points
  *
  * @param n of threads data-set will be generated by
  */
case class Parallelism(n: Int)

case class ProducerResponse(generatorsTook: FiniteDuration, producersTook: FiniteDuration)

/**
  * High performance producer of random data.
  * It can persist half a billion events with 30GB of data in 10 minutes using just 6GB of Heap.
  * It is able to generate randomly distributed data with predefined cardinality which is the main speed and data volume bottleneck
  */
class EventProducer(createGenerator: EventGeneratorFactory, consumer: EventConsumer)(p: Parallelism) extends LazyLogging with Profiling {

  def generate(batchSize: Int, maxBatchByteSize: Int, eventCount: Int): Future[List[ProducerResponse]] = {
    ArrayUtils
      .range(eventCount)
      .shuffle
      .mapAsync(p.n) { it =>
        val (producer, gTook) = profile(createGenerator(p))
        val (_, pTook) =
          profile {
            it.foldLeft(0, new ArrayBuffer[Array[Byte]](batchSize)) { case ((byteSize, acc), (idx, shuffledIdx)) =>
              def pullEvent = producer.generate(Progress(shuffledIdx, idx, eventCount))
              def pushEvents(loadSize: Int, load: ArrayBuffer[Array[Byte]]) = consumer.push(ConsumerRequest(s"${idx+1}.${producer.format}", loadSize, load.toArray))

              if (!it.hasNext) {
                // last event
                val bytes = pullEvent.getBytes
                acc.append(bytes)
                pushEvents(byteSize + bytes.length, acc)
                0 -> ArrayBuffer.empty
              } else if (byteSize > maxBatchByteSize || acc.length == batchSize) {
                // batch is ready
                val bytes = pullEvent.getBytes
                pushEvents(byteSize, acc)
                bytes.length -> new ArrayBuffer(batchSize).+=(bytes)
              } else {
                val bytes = pullEvent.getBytes
                byteSize + bytes.length -> acc.+=(bytes)
              }
            }
          }
        ProducerResponse(gTook, pTook)
      }
    }
}