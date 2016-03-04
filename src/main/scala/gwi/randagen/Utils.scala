package gwi.randagen

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.SplittableRandom

import scala.collection.mutable.ArrayBuffer
import scala.collection.Iterator
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

case class Clock(pattern: String, unit: ChronoUnit, start: LocalDateTime = LocalDateTime.now()) {
  private val formatter = DateTimeFormatter.ofPattern(pattern)
  def rewindForwardBy(millis: Int): String = start.plus(millis, unit).format(formatter)
}

object ArrayUtils {

  /**
    * Array.range(0, size) is way slower because it uses ArrayBuilder
    */
  def range(size: Int): Array[Int] = {
    val arr = new Array[Int](size)
    var counter = 0
    while (counter < size) {
      arr.update(counter, counter)
      counter += 1
    }
    arr
  }

  def partitionIntervals(length: Int, partitionCount: Int): IndexedSeq[(Int, Int)] = {
    require(length/partitionCount >= 2, "Array of size x can be partitioned by max x/2, partitioning becomes meaningless otherwise !!!")
    val partsSizes = ArrayBuffer.fill(Math.min(length, partitionCount))(length / partitionCount)
    (0 until length % partitionCount).foldLeft(partsSizes) { case (acc, idx) =>
      acc.update(idx, acc(idx) + 1)
      acc
    }.foldLeft(ArrayBuffer.empty[(Int,Int)]) {
      case (acc, e) if acc.isEmpty =>
        acc += 0 -> (e-1)
      case (acc, e) =>
        val last = acc.last._2
        acc += (last+1) -> (last+e)
    }.toIndexedSeq
  }

  /**
    * @param xs to flatten
    * @param byteSize optional total size of the future array - for performance reasons, summing up 100 000 integers can be expensive
    * @note that this method exists merely because it is the only way to be sure that SDK doesn't perform Boxing of primitives
    */
  def flattenArray(xs: Array[Array[Byte]], byteSize: Option[Int] = Option.empty): Array[Byte] =
    xs.foldLeft((0, new Array[Byte](byteSize.getOrElse(xs.iterator.map(_.length).sum)))) { case ((arrIdx, targetArr), event) =>
      val size = event.length
      System.arraycopy(event, 0, targetArr, arrIdx, size)
      size+arrIdx -> targetArr
    }._2

  /**
    * This is basically a rewrite of Scala's ParArray, in order to :
    * 1) improve resource management
    *   Memory wise :
    *     Array.range(0, 300 000 000)             // 2GB on Heap needed
    *     Array.range(0, 70 000 000).toParArray   // max you can get with 2GB on Heap
    *   Performance wise :
    *     To shuffle Generic Array of length 1 billion takes 40 seconds, but only 20 seconds using Int Array
    * 2) perform initialization logic before asynchronous execution, because Commons Math is mostly not thread safe
    */
  implicit class IntArrayPimp(underlying: Array[Int]) {

    private[randagen] def arrayIterator(fromTo: (Int,Int)): Iterator[(Int, Int)] = {
      val (fromIdx, toIdx) = fromTo
      require(toIdx<underlying.length && fromIdx<=toIdx, s"from $fromIdx to $toIdx is not within ${underlying.length} range !!!")
      new Iterator[(Int, Int)] {
        private var idx = fromIdx
        def hasNext: Boolean = idx <= toIdx
        def next() = {
          val result = idx -> underlying(idx)
          idx += 1
          result
        }
      }
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
    def shuffle: Array[Int] = {
      val rd = new SplittableRandom()
      def swap(i1: Int, i2: Int) = {
        val tmp = underlying(i1)
        underlying(i1) = underlying(i2)
        underlying(i2) = tmp
      }
      var counter = underlying.length
      while (counter > 1) {
        swap(counter - 1, rd.nextInt(counter))
        counter -= 1
      }
      underlying
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    def mapAsync[R](parallelism: Int)(fn: Iterator[(Int,Int)] => R): Future[List[R]] =
      Future.sequence(partitionIntervals(underlying.length, parallelism).toList.map(arrayIterator).map(it => Future(fn(it))))

  }
}
