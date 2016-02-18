package gwi.randagen

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.time.{Month, LocalDateTime}

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.io.Source

class TestEventDefFactory(pathPattern: String, timestampPattern: String) extends EventDefFactory {
  def apply(implicit p: Parallelism): EventDef = {
    val start = LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0)
    val pathDef = TimePathDef(Clock(pathPattern, ChronoUnit.MILLIS, start))
    val fieldDefs =
      List(
        FieldDef(
          "time",
          Linear,
          TimeValueDef(Clock(timestampPattern, ChronoUnit.MILLIS, start))
        )
      )
    EventDef(fieldDefs, Some(pathDef))
  }
}

class RanDaGenTestSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  val tmpDir = new File(sys.props("java.io.tmpdir") + "/" + scala.util.Random.nextInt(1000))
  tmpDir.mkdirs()

  override def afterAll = deleteDir(tmpDir)

  private def deleteDir(dir: File): Unit = {
    if (dir.exists()) {
      dir.listFiles.foreach { f =>
        if (f.isDirectory)
          deleteDir(f)
        else
          f.delete
      }
      Files.delete(dir.toPath)
    }
  }

  private def listAllFiles(dir: File, result: List[File] = List.empty): List[File] = {
    dir.listFiles.toList.flatMap { f =>
      if (f.isDirectory)
        result ++ listAllFiles(f, result)
      else
        f :: result
    }
  }

  private def testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(byteSize: Int) = {
    def targetDir = listAllFiles(tmpDir).head.getParentFile.getParentFile
    val pathPattern = "yyyy'/'MM'/'dd'/'HH'/'mm'/'ss"
    val timestampPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    val fieldFormatter = new SimpleDateFormat(timestampPattern)
    val directoryFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    val f = RanDaGen.run(byteSize, 10000, Parallelism(4), DsvEventGenerator(TsvFormat), FsEventConsumer(tmpDir.toPath), new TestEventDefFactory(pathPattern, timestampPattern))
    whenReady(f) { r =>
      targetDir.listFiles().foreach { d =>
        val timestamps =
          d.listFiles().map(Source.fromFile).flatMap(_.getLines).map { timeStamp =>
            directoryFormatter.format(fieldFormatter.parse(timeStamp))
          }
        assert(timestamps.toSet.size == 1, "Time path must contain only timestamps that belong to it!!!")
      }
    }
  }

  "RanDaGen should generate files based on" - {
    "path provided" in testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(1000*1000)
    "byte size and path provided" in testThatEventsInFilesOnTimeBasedPathHaveCorrectTimestamps(100)
  }
}
