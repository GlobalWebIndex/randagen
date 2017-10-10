package gwi.randagen

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object RanDaGen {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  def run(batchByteSize: Int, totalEventCount: Int, p: Parallelism, generator: EventGenerator, consumer: EventConsumer, eventDef: EventDefFactory): Future[Report] = {
    def startProducer =
      new EventProducer(eventDef, generator, consumer)(p)
        .generate(batchByteSize, totalEventCount)
        .andThen {
          case Failure(ex) =>
            logger.error("Data generation failed !!!", ex)
            consumer.kill()
          case Success(_) =>
            logger.info("Data generation succeeded ...")
            consumer.kill()
        }

    consumer.start()
    val producerFuture = startProducer
    consumer.join()
    producerFuture.map(Report(totalEventCount, _, consumer.getResponses))
  }

}