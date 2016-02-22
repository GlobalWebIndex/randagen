package gwi.randagen

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.io.Source
import scala.util.{Success, Failure, Try}

case class SampleEvent(time: String, gwid: String, country: String, section: Double, purchase: String, kv_shared: String, kv_unique: String, price: Double)

class RanDaGenTestSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val futurePatience = PatienceConfig(timeout =  Span(5, Seconds), interval = Span(200, Millis))

  def getTmpDir = {
    val tmpDir = new File(sys.props("java.io.tmpdir") + "/" + "randagen" + "/" + scala.util.Random.nextInt(1000))
    tmpDir.mkdirs()
    tmpDir
  }

  override def afterAll = deleteDir(getTmpDir.getParentFile)

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
    val tmpDir = getTmpDir
    def targetDir = listAllFiles(tmpDir).head.getParentFile.getParentFile
    val timestampPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    val fieldFormatter = new SimpleDateFormat(timestampPattern)
    val directoryFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH")
    val f = RanDaGen.run(byteSize, 10000, Parallelism(4), DsvEventGenerator(TsvFormat), FsEventConsumer(tmpDir.toPath), SampleEventDefFactory)
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
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val tmpDir = getTmpDir
    val f = RanDaGen.run(1000*1000, 30000, Parallelism(4), JsonEventGenerator, FsEventConsumer(tmpDir.toPath), SampleEventDefFactory)
    whenReady(f) { r =>
      val gwidSet =
        listAllFiles(tmpDir).map(Source.fromFile).flatMap(_.getLines()).map { line =>
          val record = mapper.readValue[Map[String, Any]](line)
          record("gwid")
        }.toSet
      assertResult(15001)(gwidSet.size)
    }
  }

  "test generated data integrity" in {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val tmpDir = getTmpDir
    val f = RanDaGen.run(1000*1000, 30000, Parallelism(4), JsonEventGenerator, FsEventConsumer(tmpDir.toPath), SampleEventDefFactory)
    whenReady(f) { r =>
      listAllFiles(tmpDir).map(Source.fromFile).flatMap(_.getLines()).map { line =>
        mapper.readValue[Map[String, Any]](line)
      }
    }

  }

}
