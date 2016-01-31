package gwi.randagen

import java.nio.file.Paths

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.scalalogging.LazyLogging
import BigDecimal.RoundingMode.HALF_UP
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Report(producerResponses: List[ProducerResponse], consumerResponses: List[ConsumerResponse])

object RanDaGen extends App with LazyLogging {

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

  private def print(start: Long, report: Report) = println {
    s"""
       |total time :
       |${(System.currentTimeMillis() - start) / 1000D} s
       |
       |event generator creation took :
       |${report.producerResponses.map(_.generatorsTook.toMillis).zipWithIndex.map(t => s"${t._2} thread : ${t._1} ms").mkString("\n")}
       |
       |data generation took :
       |${report.producerResponses.map(_.producersTook.toMillis).zipWithIndex.map(t => s"${t._2} thread : ${t._1/1000} s").mkString("\n")}
       |
       |persistence by consumer took :
       |${report.consumerResponses.map(_.took).sum / 1000D} s
       |
       |number of batches/files :
       |${report.consumerResponses.size}
       |
       |data size :
       |${BigDecimal(report.consumerResponses.map(_.byteSize.toLong).sum / (1000 * 1000D)).setScale(2, HALF_UP).toString} MB
          """.stripMargin
  }

  private def runMain(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, parallelism: Int, storage: String, path: String, dataSetName: String) = {
    val start = System.currentTimeMillis()
    run(
      batchSize,
      maxBatchSize_MB,
      totalEventCount,
      Parallelism(parallelism),
      consumerFor(storage, path)
    )(EventGenerator.factory(dataSetName, format)).map(print(start, _))
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
    producerFuture.map(Report(_, consumer.getResponses))
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
