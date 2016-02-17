package gwi.randagen

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object RanDaGen extends App {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private def runMain(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, parallelism: Int, storage: String, path: String) = {
    val f =
      run(
        batchSize,
        maxBatchSize_MB,
        totalEventCount,
        Parallelism(parallelism),
        EventGenerator(format),
        EventConsumer(storage, path),
        SampleEventDef
      )
    println(Await.result(f, 4.hours))
  }

  def run(batchEventSize: Int, batchByteSize: Int, totalEventCount: Int, p: Parallelism, generator: EventGenerator, consumer: EventConsumer, eventDef: EventDef): Future[Report] = {
    def startConsumer = {
      val consumerThread = new Thread(consumer)
      consumerThread.start()
      consumerThread
    }

    def startProducer =
      new EventProducer(eventDef, generator, consumer)(p)
        .generate(batchEventSize, batchByteSize * 1000 * 1000, totalEventCount)
        .andThen {
          case Failure(ex) =>
            logger.error("Data generation failed !!!", ex)
            consumer.kill()
          case Success(times) =>
            consumer.kill()
        }

    val consumerThread = startConsumer
    val producerFuture = startProducer
    consumerThread.join()
    producerFuture.map(Report(totalEventCount, _, consumer.getResponses))
  }

  args.toList match {
    case format :: batchSize :: maxBatchSize_MB :: totalEventCount :: parallelism :: storage :: path :: Nil =>
      runMain(format, batchSize.toInt, maxBatchSize_MB.toInt, totalEventCount.toInt, parallelism.toInt, storage, path)
    case x =>
      println(
        s"""
          | Wrong arguments : ${x.mkString(" ")}
          | Please see :
          |
          |format    batchEventSize batchByteSize  totalEventCount  parallelism  storage   path
          |---------------------------------------------------------------------------------------------
          |tsv          2000000         50              10000000         2          s3   bucket@foo/bar
          |csv          2000000         50              10000000         4          fs   /tmp/data
          |json         2000000         50              10000000         4          fs   /tmp/data
          |pretty-json  2000000         50              10000000         2          s3   bucket@foo/bar
        """.stripMargin)
  }

}
