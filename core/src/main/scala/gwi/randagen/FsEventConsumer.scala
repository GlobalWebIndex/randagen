package gwi.randagen

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Path

import scala.util.control.NonFatal

case class FsEventConsumer(targetDir: Path, compress: Boolean) extends EventConsumer {
  targetDir.toFile.mkdirs()
  lazy val id = s"FS ${targetDir.toAbsolutePath.toString}"

  def consume(req: ConsumerRequest): ConsumerResponse = {
    val start = System.currentTimeMillis()
    val ConsumerRequest(pathOpt, eventIdx, ext, bytes) = req
    val name = s"$eventIdx.$ext" + (if (compress) ".gz" else "")
    val file =
      pathOpt match {
        case Some(path) =>
          val dir = targetDir.resolve(path)
          dir.toFile.mkdirs()
          dir.resolve(name).toFile
        case None => targetDir.resolve(name).toFile
      }
    require(bytes.length > 0, s"Please don't flush empty content to file ${file.getAbsolutePath}")
    val rwChannel = new RandomAccessFile(file, "rw").getChannel
    try {
      val data = if (compress) gzip(bytes) else bytes
      rwChannel.map(FileChannel.MapMode.READ_WRITE, 0, data.length).put(data)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"Error while writing to file ${file.getAbsolutePath}", ex)
        throw ex
    } finally rwChannel.close()

    ConsumerResponse(id, name, bytes.length, System.currentTimeMillis() - start)
  }

}
