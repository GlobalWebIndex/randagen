package gwi.randagen

import com.google.cloud.storage.{BlobId, BlobInfo, Storage}

import scala.util.Try

case class GcsEventConsumer(bucket: String, path: String, storage: Storage, compress: Boolean) extends EventConsumer {
  lazy val id = s"GCS $bucket:$path"
  def consume(req: ConsumerRequest): ConsumerResponse = {
    val start = System.currentTimeMillis()
    val ConsumerRequest(pathOpt, eventIdx, ext, bytes) = req
    val key = s"$eventIdx.$ext" + (if (compress) ".gz" else "")
    val fullPath = pathOpt match {
      case Some(p) => s"$path/$p/$key"
      case None => s"$path/$key"
    }
    val blobId = BlobId.of(bucket, fullPath)
    val data = if (compress) gzip(bytes) else bytes
    val contentType = if (compress) "gzip" else "text/plain"
    val blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build()
    Try(storage.create(blobInfo, data))
      .map(_ => ConsumerResponse(id, key, bytes.length, System.currentTimeMillis() - start)).get
  }
}
