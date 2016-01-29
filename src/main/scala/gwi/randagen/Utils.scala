package gwi.randagen

import java.util.SplittableRandom

import scala.collection.{AbstractIterator, Iterator}

object Utils {

  def shuffledArray(start: Int, size: Int, step: Int = 1): Array[Int] = shuffle(Array.range(start, size, step))

  def shuffledIterator(start: Int, size: Int, step: Int = 1): Iterator[Int] = {
    val arr = shuffledArray(start, size, step)
    new AbstractIterator[Int] {
      private var idx = 0
      def hasNext: Boolean = idx < arr.length
      def next(): Int = {
        val result = arr(idx)
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
  def shuffle[T](arr: Array[T]) = {
    val rd = new SplittableRandom()
    def swap(i1: Int, i2: Int) = {
      val tmp = arr(i1)
      arr(i1) = arr(i2)
      arr(i2) = tmp
    }
    for (n <- arr.length to 2 by -1) {
      swap(n - 1, rd.nextInt(n))
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
}