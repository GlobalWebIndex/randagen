package gwi.randagen

import java.text.SimpleDateFormat

import org.scalatest.time.{Millis, Seconds, Span}

import scala.io.Source
import scala.util.{Failure, Success, Try}

case class SampleEvent(time: String, uuid: String, country: String, section: Double, purchase: String, kv_shared: String, kv_unique: String, price: Double)

class RanDaGenTestSuite extends BaseSuite {
  implicit val futurePatience = PatienceConfig(timeout =  Span(5, Seconds), interval = Span(200, Millis))

  override def afterAll = deleteDir(getTmpDir.getParentFile)

  private def testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(byteSize: Int) = {
    val tmpDir = getTmpDir
    def targetDir = listAllFiles(tmpDir).head.getParentFile.getParentFile
    val fieldFormatter = new SimpleDateFormat(SampleEventDefFactory.TimeStampPattern)
    val directoryFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH")
    val f = RanDaGen.run(byteSize, 10000, Parallelism(4), DsvEventGenerator(TsvFormat), FsEventConsumer(tmpDir.toPath, compress = false), SampleEventDefFactory())
    whenReady(f) { r =>
      targetDir.listFiles().foreach { d =>
        val timestamps =
          d.listFiles().map(Source.fromFile).flatMap(_.getLines).map { line =>
            Try(fieldFormatter.parse(line)) match {
              case Failure(ex) => println(line)
              case Success(_) =>
            }
            directoryFormatter.format(fieldFormatter.parse(line.takeWhile(_ != '\t')))
          }
        assert(timestamps.toSet.size == 1, "Time path must contain only timestamps that belong to it!!!")
      }
    }
  }

  "RanDaGen should generate files based on" - {
    "path provided" in testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(10*1000*1000)
    "byte size and path provided" in testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(10*1000)
  }

  "test random distribution" in {
    val totalEventCount = 30000
    val tmpDir = getTmpDir
    val f = RanDaGen.run(1000*1000, totalEventCount, Parallelism(4), JsonEventGenerator, FsEventConsumer(tmpDir.toPath, compress = false), SampleEventDefFactory())
    whenReady(f) { r =>
      val uuidSet =
        listAllFiles(tmpDir).map(Source.fromFile).flatMap(_.getLines()).map { line =>
          val record = ObjMapper.readValue[Map[String, Any]](line)
          record("uuid")
        }.toSet
      assertResult(15001)(uuidSet.size)
    }
  }

  "test generated data integrity" in {
    val totalEventCount = 30000
    val tmpDir = getTmpDir
    val f = RanDaGen.run(1000*1000, totalEventCount, Parallelism(4), JsonEventGenerator, FsEventConsumer(tmpDir.toPath, compress = false), SampleEventDefFactory())
    whenReady(f) { r =>
      listAllFiles(tmpDir).map(Source.fromFile).flatMap(_.getLines()).map(ObjMapper.readValue[Map[String, Any]])
    }
  }

}
