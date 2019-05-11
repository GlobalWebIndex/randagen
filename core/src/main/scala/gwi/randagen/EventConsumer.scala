package gwi.randagen

import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.GZIPOutputStream

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.google.cloud.storage.{Storage, StorageOptions}
import org.slf4j.LoggerFactory

sealed trait Request
object PoisonPill extends Request
case class ConsumerRequest(pathOpt: Option[String], eventIdx: Int, ext: String, batchLoad: Array[Byte]) extends Request
case class ConsumerResponse(id: String, name: String, byteSize: Int, took: Long)

/**
  * A bounded BlockingQueue based implementation of a Consumer being able to consume messages by
  * concurrently running publishers. It blocks publisher if it is falling behind (queue gets full)
  */
trait EventConsumer extends Thread {
  protected val logger = LoggerFactory.getLogger(getClass)
  private val responses = List.newBuilder[ConsumerResponse]
  private val queue = new LinkedBlockingQueue[Request](3)

  protected[this] def consume(req: ConsumerRequest): ConsumerResponse

  def getResponses: List[ConsumerResponse] = responses.result()
  def push(req: ConsumerRequest): Unit = queue.put(req)
  def kill(): Unit = queue.put(PoisonPill)

  protected[this] def gzip(bytes: Array[Byte]) = {
    val baos = new ByteArrayOutputStream(bytes.length)
    val gzip = new GZIPOutputStream(baos, bytes.length)
    try {
      gzip.write(bytes)
      gzip.close()
      baos.toByteArray
    } finally {
      gzip.close()
    }
  }

  override def run() =
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

object EventConsumer {
  private def s3Client = {
    def getEnv(key: String) = sys.env.getOrElse(key, throw new IllegalStateException(s"Please export $key as an environment variable !!!"))
    val s3Client = new AmazonS3Client(new BasicAWSCredentials(getEnv("AWS_ACCESS_KEY_ID"), getEnv("AWS_SECRET_ACCESS_KEY")))
    s3Client.setRegion(Region.getRegion(Regions.fromName(getEnv("AWS_DEFAULT_REGION"))))
    s3Client
  }

  private def gscClient: Storage = StorageOptions.getDefaultInstance.getService

  def apply(storage: String, path: String, compress: Boolean): EventConsumer = storage match {
    case "fs" =>
      FsEventConsumer(Paths.get(path), compress)
    case "gcs" if path.contains('@') =>
      val parts = path.split('@')
      GcsEventConsumer(parts(0), parts(1), gscClient, compress)
    case "s3" if path.contains('@') =>
      val parts = path.split('@')
      S3EventConsumer(parts(0), parts(1), s3Client, compress)
    case _ =>
      throw new IllegalArgumentException(s"Storage $storage and path $path are not valid !")
  }
}