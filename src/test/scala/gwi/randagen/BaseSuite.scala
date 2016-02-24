package gwi.randagen

import java.io.File
import java.nio.file.Files

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, FreeSpec}


trait BaseSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import upickle.default._
  implicit def MapReader: Reader[Map[String, Any]] = Reader[Map[String, Any]] {
    case x: upickle.Js.Obj => x.value.map(t => (t._1, t._2.value)).toMap
  }

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
