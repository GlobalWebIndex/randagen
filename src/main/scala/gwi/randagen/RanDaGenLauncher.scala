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

  private def producerOf(dataType: String, dataSetDef: DataSetDef) = dataType match {
    case "json" =>
      JsonEventProducer(dataSetDef)
    case extension =>
      DsvEventProducer(extension, dataSetDef)
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

  def run(dataType: String, batchSize: Int, eventCount: Int, storage: String, path: String, dataSetDef: DataSetDef): Future[List[BatchRes]] =
    generate(
      batchSize,
      eventCount,
      producerOf(dataType, dataSetDef),
      consumersFor(storage.split(",").zip(path.split(",")).toList)
    )

  args.toList match {
    case dataType :: dataSetName :: batchSize :: eventCount :: storage :: path :: jsonDef :: Nil =>
      val dataSetDef = DataSetDef.deserialize(Paths.get(jsonDef))
      val f = run(dataType, batchSize.toInt, eventCount.toInt, storage, path, dataSetDef).map { batchResponses =>
        val responsesByConsumer = batchResponses.groupBy(_.id)
        s"""
          |persistence took :
          |${responsesByConsumer.mapValues(_.map(_.took).sum / 1000D).mkString("\n")}
          |
          |data stored took :
          |${responsesByConsumer.mapValues(_.map(_.name).mkString(" ")).mkString("\n")}
          |
          |data size in MB :
          |${responsesByConsumer.mapValues(_.map(_.byteSize.toLong).sum).mapValues(size => BigDecimal(size / (1000*1000D)).setScale(2, HALF_UP).toString).mkString("\n")}
        """.stripMargin
      }
      println(Await.result(f, 1.hour))
    case _ =>
      println(
        s"""
          | Wrong arguments, examples :
          |   dataType   dataSet   batchSize   eventCount   storage       path          jsonDataSetDefinition
          |   -----------------------------------------------------------------------------------------------
          |   tsv         gwiq      200000      10000000    s3       bucket@foo/bar       /tmp/def.json
          |   csv         gwiq      200000      10000000    fs       /tmp                 /tmp/def.json
          |   json        gwiq      200000      10000000    fs,s3    /tmp,bucket@foo/bar  /tmp/def.json
          |
          | Example data-set definition :
          | ${DataSetDef.serialize(DataSetDef.sampleDataSetDef, 4)}
        """.stripMargin)
  }

}
