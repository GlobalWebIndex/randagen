package gwi.randagen

import java.nio.file.Paths

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object RanDaGen extends App {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private def s3Client = {
    def getEnv(key: String) = sys.env.getOrElse(key, throw new IllegalStateException(s"Please export $key as an environment variable !!!"))
    val s3Client = new AmazonS3Client(new BasicAWSCredentials(getEnv("AWS_ACCESS_KEY_ID"), getEnv("AWS_SECRET_ACCESS_KEY")))
    s3Client.setRegion(Region.getRegion(Regions.fromName(getEnv("AWS_DEFAULT_REGION"))))
    s3Client
  }

  private def consumerFor(storage: String, path: String) = storage match {
    case "fs" =>
      FsEventConsumer(Paths.get(path))
    case s3 if path.contains('@') =>
      val parts = path.split('@')
      S3EventConsumer(parts(0), parts(1), s3Client)
    case _ =>
      throw new IllegalArgumentException(s"Storage $storage and path $path are not valid !")
  }

  private def runMain(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, parallelism: Int, storage: String, path: String, dataSetName: String) = {
    val f =
      run(
        batchSize,
        maxBatchSize_MB,
        totalEventCount,
        Parallelism(parallelism),
        consumerFor(storage, path)
      )(EventGenerator.factory(format, dataSetName))
    println(Await.result(f, 4.hours))
  }

  def run(batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, p: Parallelism, consumer: EventConsumer)(factory: EventGeneratorFactory): Future[Report] = {
    def startConsumer = {
      val consumerThread = new Thread(consumer)
      consumerThread.start()
      consumerThread
    }

    def startProducer =
      new EventProducer(factory, consumer)(p)
        .generate(batchSize, maxBatchSize_MB * 1000000, totalEventCount)
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
    case format :: dataSetName :: batchSize :: maxBatchSize_MB :: totalEventCount :: parallelism :: storage :: path :: Nil =>
      runMain(format, batchSize.toInt, maxBatchSize_MB.toInt, totalEventCount.toInt, parallelism.toInt, storage, path, dataSetName)
    case x =>
      println(
        s"""
          | Wrong arguments : ${x.mkString(" ")}
          | Please see :
          |
          |   format   dataSet   batchSize   maxBatchSize-MB    totalEventCount  parallelism  storage        path
          |   ------------------------------------------------------------------------------------------------------------
          |   tsv       sample    2000000         50                10000000         4          s3         bucket@foo/bar
          |   csv       sample    2000000         50                10000000         4          fs         /tmp
          |   json      sample    2000000         50                10000000         4          fs         /tmp
          |
        """.stripMargin)
  }

}
