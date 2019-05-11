package gwi.randagen

import java.io.ByteArrayInputStream

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata

import scala.util.Try

case class S3EventConsumer(bucket: String, path: String, s3: AmazonS3Client, compress: Boolean) extends EventConsumer {
  lazy val id = s"S3 $bucket:$path"
  def consume(req: ConsumerRequest): ConsumerResponse = {
    val start = System.currentTimeMillis()
    val ConsumerRequest(pathOpt, eventIdx, ext, bytes) = req
    val key = s"$eventIdx.$ext" + (if (compress) ".gz" else "")
    val metaData = new ObjectMetadata()
    val fullPath = pathOpt match {
      case Some(p) => s"$path/$p/$key"
      case None => s"$path/$key"
    }
    val data = if (compress) gzip(bytes) else bytes
    metaData.setContentLength(data.length)
    Try(s3.putObject(bucket, fullPath, new ByteArrayInputStream(data), metaData)).map(_ => ConsumerResponse(id, key, bytes.length, System.currentTimeMillis() - start)).get
  }
}
