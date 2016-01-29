package gwi.randagen

import java.io.{ByteArrayInputStream, RandomAccessFile}
import java.nio.channels.FileChannel
import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata

import scala.concurrent.Future
import scala.util.Try

case class BatchReq(name: String, totalSize: Int, load: Iterable[Array[Byte]])
case class BatchRes(id: String, name: String, byteSize: Int, took: Long)

sealed trait EventConsumer {
  def consume(req: BatchReq): Future[BatchRes]
}

case class FsEventConsumer(targetDir: Path) extends EventConsumer {
  targetDir.toFile.mkdirs()
  lazy val id = s"FS ${targetDir.toAbsolutePath.toString}"

  def consume(req: BatchReq): Future[BatchRes] = Future[BatchRes] {
    val start = System.currentTimeMillis()
    val BatchReq(name, size, load) = req
    val bytes = Utils.flattenArray(load, Option(size))
    val byteSize = bytes.length
    val file = targetDir.resolve(name).toFile
    require(byteSize > 0, s"Please don't flush empty content to file ${file.getAbsolutePath}")
    val rwChannel = new RandomAccessFile(file, "rw").getChannel
    try rwChannel.map(FileChannel.MapMode.READ_WRITE, 0, byteSize).put(bytes) finally rwChannel.close()
    BatchRes(id, name,  byteSize, System.currentTimeMillis() - start)
  }
}

case class S3EventConsumer(bucket: String, path: String, s3: AmazonS3Client) extends EventConsumer {
  lazy val id = s"S3 $bucket:$path"
  def consume(req: BatchReq): Future[BatchRes] = Future[BatchRes] {
    val start = System.currentTimeMillis()
    val BatchReq(key, size, load) = req
    val bytes = Utils.flattenArray(load, Option(size))
    val metaData = new ObjectMetadata()
    val fullPath = s"$path/$key"
    metaData.setContentLength(size)
    Try(s3.putObject(bucket, fullPath, new ByteArrayInputStream(bytes), metaData)).map(_ => BatchRes(id, key, size, System.currentTimeMillis() - start)).get
  }
}