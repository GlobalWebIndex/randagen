package gwi.randagen

import java.nio.file.Paths

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.scalalogging.LazyLogging
import BigDecimal.RoundingMode.HALF_UP
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

case class Report(producerResponses: List[ProducerResponse], consumerResponses: List[ConsumerResponse]) {
  def scale(value: Double) = BigDecimal(value).setScale(2, HALF_UP)
  def zipByThread(xs: Iterable[Long]) = xs.zipWithIndex.map(t => s"${t._2} thread : ${t._1} ms").mkString("\n", "\n", "")
  override def toString = {
    lazy val generators = zipByThread(producerResponses.map(_.generatorsTook.toMillis))
    lazy val production = zipByThread(producerResponses.map(_.producersTook.toMillis))
    lazy val persistence = consumerResponses.map(_.took).sum / 1000D
    lazy val dataSize = scale(consumerResponses.map(_.byteSize.toLong).sum / (1000 * 1000D)).toDouble
    lazy val throughPut = scale(dataSize / producerResponses.map(_.producersTook.toMillis).sorted.last * 1000D)
    s"""
       |event generator creation took : $generators
       |data production took : $production
       |persistence by consumer took : $persistence s
       |number of batches/files : ${consumerResponses.size}
       |data size : $dataSize MB
       |throughput : $throughPut MB/s
    """.stripMargin
  }
}

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

  private def runMain(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, parallelism: Int, storage: String, path: String, dataSetName: String) = {
    val f =
      run(
        batchSize,
        maxBatchSize_MB,
        totalEventCount,
        Parallelism(parallelism),
        consumerFor(storage, path)
      )(EventGenerator.factory(format, dataSetName)).map(println)
    Await.ready(f, 1.hour)
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
