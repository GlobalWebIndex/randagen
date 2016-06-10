package gwi.randagen.app

import gwi.randagen._

import scala.concurrent.Await
import scala.concurrent.duration._

object RanDaGenApp extends App {

  private def runMain(format: String, batchByteSize: Double, totalEventCount: Int, parallelism: Int, storage: String, compress: Boolean, path: String) = {
    val future =
      RanDaGen.run(
        batchByteSize.toInt,
        totalEventCount,
        Parallelism(parallelism),
        EventGenerator(format),
        EventConsumer(storage, path, compress),
        SampleEventDefFactory()
      )
    println(Await.result(future, 4.hours))
  }

  args.toList match {
    case format :: batchSize_MB :: totalEventCount :: parallelism :: storage :: compress :: path :: Nil =>
      runMain(format, batchSize_MB.toDouble * 1000 * 1024, totalEventCount.toInt, parallelism.toInt, storage, compress.toBoolean, path)
    case x =>
      println(
        s"""
          | Wrong arguments : ${x.mkString(" ")}
          | Please see :
          |
          |format  batchByteSize_MB  totalEventCount  parallelism  storage  compress  path
          |---------------------------------------------------------------------------------------------
          |tsv          50              10000000         2          s3       true     bucket@foo/bar
          |csv          50              10000000         4          fs       false    /tmp/data
          |json         50              10000000         4          fs       false    /tmp/data
        """.stripMargin)
  }

}
