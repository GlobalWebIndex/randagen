package gwi.randagen

import java.io.File
import java.nio.file.Files

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.util.{MinimalPrettyPrinter, DefaultPrettyPrinter}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, FreeSpec}

/**
  * @note that ObjectMapper is thread safe after initialization so it can be reused by multiple threads
  */
object ObjMapper extends ObjectMapper with ScalaObjectMapper {
  setSerializationInclusion(JsonInclude.Include.NON_NULL)
  registerModule(DefaultScalaModule)
  val prettyWriter = writer(new DefaultPrettyPrinter)
  val miniWriter = writer(new MinimalPrettyPrinter)
}

trait BaseSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  protected def getTmpDir = {
    val tmpDir = new File(sys.props("java.io.tmpdir") + "/" + "randagen" + "/" + scala.util.Random.nextInt(1000))
    tmpDir.mkdirs()
    tmpDir
  }

  protected def deleteDir(dir: File): Unit = {
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

  protected def listAllFiles(dir: File, result: List[File] = List.empty): List[File] = {
    dir.listFiles.toList.flatMap { f =>
      if (f.isDirectory)
        result ++ listAllFiles(f, result)
      else
        f :: result
    }
  }

}
