package gwi.randagen

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object RanDaGen extends App {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private def runMain(format: String, batchByteSize: Int, totalEventCount: Int, parallelism: Int, storage: String, path: String) = {
    val f =
      run(
        batchByteSize,
        totalEventCount,
        Parallelism(parallelism),
        EventGenerator(format),
        EventConsumer(storage, path),
        SampleEventDefFactory
      )
    println(Await.result(f, 4.hours))
  }

  def run(batchByteSize: Int, totalEventCount: Int, p: Parallelism, generator: EventGenerator, consumer: EventConsumer, eventDef: EventDefFactory): Future[Report] = {
    def startConsumer = {
      val consumerThread = new Thread(consumer)
      consumerThread.start()
      consumerThread
    }

    def startProducer =
      new EventProducer(eventDef, generator, consumer)(p)
        .generate(batchByteSize, totalEventCount)
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
    case format :: batchSize_MB :: totalEventCount :: parallelism :: storage :: path :: Nil =>
      runMain(format, batchSize_MB.toInt * 1024 * 1000, totalEventCount.toInt, parallelism.toInt, storage, path)
    case x =>
      println(
        s"""
          | Wrong arguments : ${x.mkString(" ")}
          | Please see :
          |
          |format  batchByteSize_MB  totalEventCount  parallelism  storage  path
          |---------------------------------------------------------------------------------------------
          |tsv          50              10000000         2          s3   bucket@foo/bar
          |csv          50              10000000         4          fs   /tmp/data
          |json         50              10000000         4          fs   /tmp/data
        """.stripMargin)
  }

}
