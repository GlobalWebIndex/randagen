package gwi.randagen

import java.io.{ByteArrayInputStream, RandomAccessFile}
import java.nio.channels.FileChannel
import java.nio.file.{Paths, Path}
import java.util.concurrent.LinkedBlockingQueue

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import org.slf4j.LoggerFactory

import scala.util.Try

sealed trait Request
object PoisonPill extends Request
case class ConsumerRequest(pathOpt: Option[String], eventIdx: Int, ext: String, totalSize: Int, batchLoad: Array[Array[Byte]]) extends Request
case class ConsumerResponse(id: String, name: String, byteSize: Int, took: Long)

/**
  * A bounded BlockingQueue based implementation of a Consumer being able to consume messages by
  * concurrently running publishers. It blocks publisher if it is falling behind (queue gets full)
  */
sealed trait EventConsumer extends Runnable {
  private val logger = LoggerFactory.getLogger(getClass)
  private val responses = List.newBuilder[ConsumerResponse]
  private val queue = new LinkedBlockingQueue[Request](3)

  protected[this] def consume(req: ConsumerRequest): ConsumerResponse

  def getResponses: List[ConsumerResponse] = responses.result()
  def push(req: ConsumerRequest): Unit = queue.put(req)
  def kill(): Unit = queue.put(PoisonPill)

  def run() =
    try {
      var shutdown = false
      while (!shutdown)
        queue.take() match {
          case req: ConsumerRequest => responses += consume(req)
          case PoisonPill => shutdown = true
        }
    } catch {
      case ex: InterruptedException =>
        logger.error("Consumer thread interrupted !!!", ex)
    }
}

case class FsEventConsumer(targetDir: Path) extends EventConsumer {
  targetDir.toFile.mkdirs()
  lazy val id = s"FS ${targetDir.toAbsolutePath.toString}"

  def consume(req: ConsumerRequest): ConsumerResponse = {
    val start = System.currentTimeMillis()
    val ConsumerRequest(pathOpt, eventIdx, ext, size, load) = req
    val name = s"$eventIdx.$ext"
    val bytes = ArrayUtils.flattenArray(load, Option(size))
    val byteSize = bytes.length
    val file =
      pathOpt match {
        case Some(path) =>
          val dir = targetDir.resolve(path)
          dir.toFile.mkdirs()
          dir.resolve(name).toFile
        case None => targetDir.resolve(name).toFile
      }
    require(byteSize > 0, s"Please don't flush empty content to file ${file.getAbsolutePath}")
    val rwChannel = new RandomAccessFile(file, "rw").getChannel
    try rwChannel.map(FileChannel.MapMode.READ_WRITE, 0, byteSize).put(bytes) finally rwChannel.close()
    ConsumerResponse(id, name,  byteSize, System.currentTimeMillis() - start)
  }

}

case class S3EventConsumer(bucket: String, path: String, s3: AmazonS3Client) extends EventConsumer {
  lazy val id = s"S3 $bucket:$path"
  def consume(req: ConsumerRequest): ConsumerResponse = {
    val start = System.currentTimeMillis()
    val ConsumerRequest(pathOpt, eventIdx, ext, size, load) = req
    val key = s"$eventIdx.$ext"
    val bytes = ArrayUtils.flattenArray(load, Option(size))
    val metaData = new ObjectMetadata()
    val fullPath = pathOpt match {
      case Some(p) => s"$path/$p/$key"
      case None => s"$path/$key"
    }
    metaData.setContentLength(size)
    Try(s3.putObject(bucket, fullPath, new ByteArrayInputStream(bytes), metaData)).map(_ => ConsumerResponse(id, key, size, System.currentTimeMillis() - start)).get
  }
}

object EventConsumer {
  private def s3Client = {
    def getEnv(key: String) = sys.env.getOrElse(key, throw new IllegalStateException(s"Please export $key as an environment variable !!!"))
    val s3Client = new AmazonS3Client(new BasicAWSCredentials(getEnv("AWS_ACCESS_KEY_ID"), getEnv("AWS_SECRET_ACCESS_KEY")))
    s3Client.setRegion(Region.getRegion(Regions.fromName(getEnv("AWS_DEFAULT_REGION"))))
    s3Client
  }

  def apply(storage: String, path: String): EventConsumer = storage match {
    case "fs" =>
      FsEventConsumer(Paths.get(path))
    case s3 if path.contains('@') =>
      val parts = path.split('@')
      S3EventConsumer(parts(0), parts(1), s3Client)
    case _ =>
      throw new IllegalArgumentException(s"Storage $storage and path $path are not valid !")
  }
}