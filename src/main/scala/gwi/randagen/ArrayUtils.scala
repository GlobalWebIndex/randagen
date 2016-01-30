package gwi.randagen

import java.util.SplittableRandom

import scala.collection.mutable.ArrayBuffer
import scala.collection.{AbstractIterator, Iterator}
import scala.concurrent.Future
import scala.concurrent.duration._


trait Profiling {

  def profile[R](block: => R): (R,FiniteDuration) = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    result -> (t1 - t0).nanoseconds
  }

}

object ArrayUtils {

  /**
    * Array.range(0, size) is way slower because it uses ArrayBuilder
    */
  def range(size: Int): Array[Int] = {
    val arr = new Array[Int](size)
    for (idx <- 0 until size) {
      arr.update(idx, idx)
    }
    arr
  }

  /**
    * @param xs to flatten
    * @param byteSize optional total size of the future array - for performance reasons, summing up 100 000 integers can be expensive
    * @note that this method exists merely because it is the only way to be sure that SDK doesn't perform Boxing of primitives
    */
  def flattenArray(xs: Iterable[Array[Byte]], byteSize: Option[Int] = Option.empty): Array[Byte] =
    xs.foldLeft((0, new Array[Byte](byteSize.getOrElse(xs.iterator.map(_.length).sum)))) { case ((arrIdx, targetArr), event) =>
      val size = event.length
      System.arraycopy(event, 0, targetArr, arrIdx, size)
      size+arrIdx -> targetArr
    }._2

  /**
    * This is basically a rewrite of Scala's ParArray, in order to :
    * 1) improve resource management
    *     Array.range(0, 300 000 000)             // 2GB on Heap needed
    *     Array.range(0, 70 000 000).toParArray   // max you can get with 2GB on Heap
    *
    * 2) perform initialization logic before asynchronous execution, because Commons Math is mostly not thread safe
    */
  implicit class ArrayPimp[T](underlying: Array[T]) {

    private def arrayIterators(partition: (Int,Int)): Iterator[(Int, T)] = {
      val (fromIdx, untilIdx) = partition
      new AbstractIterator[(Int, T)] {
        private var idx = fromIdx
        def hasNext: Boolean = idx <= untilIdx
        def next() = {
          val result = idx -> underlying(idx)
          idx += 1
          result
        }
      }
    }

    private def arrayPartitions(partitionCount: Int): List[(Int, Int)] = {
      val arrLength = underlying.length
      val partsSizes = ArrayBuffer.fill(Math.min(arrLength, partitionCount))(arrLength / partitionCount)
      (0 until arrLength % partitionCount).foldLeft(partsSizes) { case (acc, idx) =>
        acc.update(idx, acc(idx) + 1)
        acc
      }.foldLeft(ArrayBuffer.empty[(Int,Int)]) {
        case (acc, e) if acc.isEmpty =>
          acc += 0 -> (e-1)
        case (acc, e) =>
          val last = acc.last._2
          acc += (last+1) -> (last+e)
      }.toList
    }

    /**
      * Shuffles integers randomly. Important for generating data with
      *   1) Random distribution
      *   2) Zero duplication (100% cardinality)
      *
      * It is identical to scala.util.Random.shuffle except for it avoids Boxing primitives
      *
      * @note that for shuffling 1 billion integers you'd need 4GB+ of Heap
      *       because the primitive 32bits Ints itself take 4GBs
      *       random shuffling cannot be done lazily !!!
      */
    def shuffle: Array[T] = {
      val rd = new SplittableRandom()
      def swap(i1: Int, i2: Int) = {
        val tmp = underlying(i1)
        underlying(i1) = underlying(i2)
        underlying(i2) = tmp
      }
      for (n <- underlying.length to 2 by -1) {
        swap(n - 1, rd.nextInt(n))
      }
      underlying
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    def mapAsync[R](parallelism: Int)(fn: Iterator[(Int,T)] => R): Future[List[R]] =
      Future.sequence(arrayPartitions(parallelism).map(arrayIterators).map( it => Future(fn(it))))

  }
}
