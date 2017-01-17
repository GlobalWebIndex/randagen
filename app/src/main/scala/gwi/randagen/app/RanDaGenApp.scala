package gwi.randagen.app

import gwi.randagen._
import org.backuity.clist._
import scala.concurrent.Await
import scala.concurrent.duration._

object RanDaGenApp extends CliMain[Unit](name = "randagen") {

  var format = arg[String](description = "[ tsv | csv | json ]")
  var batchFlushMegabytesLimit = arg[Double](name = "batch-flush-megabytes-limit", description = "When to flush in-memory data to disk or network", required = false, default = 50)
  var recordsCount = arg[Int](name = "records-count", "How many records to generate")
  var parallelism = arg[Int](description = "How many threads should be leveraged for data generation")
  var storage = arg[String](description = "[ s3 | fs ]")
  var compress = arg[Boolean](description = "Whether to gzip output or not")
  var path = arg[String](description = "S3 of FS path: [ bucket@foo/bar  | /tmp/data ]")

  def run: Unit = {
    val future =
      RanDaGen.run(
        (batchFlushMegabytesLimit * 1000 * 1024).toInt,
        recordsCount,
        Parallelism(parallelism),
        EventGenerator(format),
        EventConsumer(storage, path, compress),
        SampleEventDefFactory()
      )
    println(Await.result(future, 4.hours))
  }

}
