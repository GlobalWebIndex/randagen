package gwi.randagen

import java.io.File
import java.nio.file.Files

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

class RanDaGenTestSuite extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import gwi.randagen.ArrayUtils.IntArrayPimp

  val tmpDir = new File(sys.props("java.io.tmpdir") + "/" + scala.util.Random.nextInt(1000))

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

  "flattening array should work" in {
    val target =
      Array(
        Array('a', 'b', 'c').map(_.toByte),
        Array('d', 'e', 'f').map(_.toByte),
        Array('g', 'h', 'i').map(_.toByte)
      )

    val expected = Array('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i').map(_.toByte)
    val actual = ArrayUtils.flattenArray(target)
    assertResult(expected)(actual)
  }

  "ranging array should work" in {
    assertResult(Array(0,1,2)) (ArrayUtils.range(3))
    assertResult(Array(0)) (ArrayUtils.range(1))
    assertResult(Array()) (ArrayUtils.range(0))
  }

  "array partitions should work" in {
    assertResult(List((0,11))) ((0 until 12).toArray.arrayPartitions(1))
    assertResult(List((0,5), (6,11))) ((0 until 12).toArray.arrayPartitions(2))
    assertResult(List((0,3), (4,7), (8,11))) ((0 until 12).toArray.arrayPartitions(3))
    assertResult(List((0,2), (3,5), (6,8), (9,11))) ((0 until 12).toArray.arrayPartitions(4))
    assertResult(List((0,2), (3,5), (6,7), (8,9), (10,11))) ((0 until 12).toArray.arrayPartitions(5))
    assertResult(List((0,1), (2,3), (4,5), (6,7), (8,9), (10,11))) ((0 until 12).toArray.arrayPartitions(6))
    assertThrows[IllegalArgumentException]((0 until 12).toArray.arrayPartitions(7))
  }

  "array iterators should work" in {
    assertResult(List((0,0), (1,1), (2,2))) (Array(0,1,2,3).arrayIterator((0,2)).toList)
    assertResult(List((0,0))) (Array(0,1).arrayIterator((0,0)).toList)
    assertThrows[IllegalArgumentException](Array(0,1).arrayIterator((0,2)).toList)
  }

  "array shuffle should work" in {
    assert(Array(1,2,3).shuffle.length == 3)
    assertResult(Set(1,2,3)) (Array(1,2,3).shuffle.toSet)
    assertResult(Array(1)) (Array(1).shuffle)
    assertResult(Array()) (Array[Int]().shuffle)
  }

  "map async should work" in {
    val f2 = ArrayUtils.range(6).mapAsync(3)(_.toList)
    whenReady(f2) { r =>
      assert(r.length == 3)
      assertResult(List(List((0,0), (1,1)), List((2,2), (3,3)), List((4,4), (5,5)))) (r)
    }
  }

  "RanDaGen should work" in {
    tmpDir.mkdir()
    val f = RanDaGen.run(10, 1, 120, Parallelism(4), JsonEventGenerator, FsEventConsumer(tmpDir.toPath), SampleEventDef)
    whenReady(f) { r =>
      val allFiles = listAllFiles(tmpDir)
      assert(allFiles.size == 12)
      assert(tmpDir.listFiles().length == 12)
    }
  }

}
