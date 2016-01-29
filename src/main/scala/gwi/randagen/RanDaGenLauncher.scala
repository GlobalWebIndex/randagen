package gwi.randagen

import java.nio.file.Paths

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import gwi.randagen.RanDaGen._

import scala.BigDecimal.RoundingMode.HALF_UP
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object RanDaGenLauncher extends App {
  private def s3Client = {
    def getEnv(key: String) = sys.env.getOrElse(key, throw new IllegalStateException(s"Please export $key as an environment variable !!!"))
    val s3Client = new AmazonS3Client(new BasicAWSCredentials(getEnv("AWS_ACCESS_KEY_ID"), getEnv("AWS_SECRET_ACCESS_KEY")))
    s3Client.setRegion(Region.getRegion(Regions.fromName(getEnv("AWS_DEFAULT_REGION"))))
    s3Client
  }

  private def consumersFor(storagePaths: List[(String, String)]) = storagePaths.map {
    case ("fs", path) =>
      FsEventConsumer(Paths.get(path))
    case (s3, path) if path.contains('@') =>
      val parts = path.split('@')
      S3EventConsumer(parts(0), parts(1), s3Client)
    case (storage, path) =>
      throw new IllegalArgumentException(s"Storage $storage and path $path are not valid !")
  }

  def run(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, producer: EventProducer, consumers: List[EventConsumer]): Future[List[BatchRes]] =
    generate(
      batchSize,
      maxBatchSize_MB * 1000000,
      totalEventCount,
      producer,
      consumers
    )

  private def runMain(format: String, batchSize: Int, maxBatchSize_MB: Int, totalEventCount: Int, storage: String, path: String, dataSetName: String) = {
    val start = System.currentTimeMillis()
    run(
      format,
      batchSize,
      maxBatchSize_MB,
      totalEventCount,
      EventProducer.get(dataSetName, format),
      consumersFor(storage.split(",").zip(path.split(",")).toList)
    ).map { batchResponses =>
      val responsesByConsumer = batchResponses.groupBy(_.id)
      s"""
         |total time :
         |${(System.currentTimeMillis() - start) / 1000D} s
         |
          |persistence took :
         |${responsesByConsumer.mapValues(_.map(_.took).sum / 1000D).mkString("\n")} s
         |
          |data stored :
         |${responsesByConsumer.mapValues(_.map(_.name).mkString(" ")).mkString("\n")}
         |
          |data size :
         |${responsesByConsumer.mapValues(_.map(_.byteSize.toLong).sum).mapValues(size => BigDecimal(size / (1000 * 1000D)).setScale(2, HALF_UP).toString).mkString("\n")} MB
        """.stripMargin
    }
  }

  args.toList match {
    case format :: dataSetName :: batchSize :: maxBatchSize_MB :: totalEventCount :: storage :: path :: Nil =>
      val results = runMain(format, batchSize.toInt, maxBatchSize_MB.toInt, totalEventCount.toInt, storage, path, dataSetName)
      println(Await.result(results, 1.hour))
    case _ =>
      println(
        s"""
          | Wrong arguments, examples :
          |   format   dataSet   batchSize   maxBatchSize-MB    totalEventCount   storage       path
          |   ------------------------------------------------------------------------------------------------
          |   tsv       gwiq      2000000         50                10000000        s3         bucket@foo/bar
          |   csv       gwiq      2000000         50                10000000        fs         /tmp
          |   json      gwiq      2000000         50                10000000        fs,s3      /tmp,bucket@foo/bar
          |
          | Example data-set definition :
        """.stripMargin)
  }

}
