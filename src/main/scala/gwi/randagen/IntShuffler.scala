package gwi.randagen

import scala.collection.{AbstractIterator, Iterator}
import scala.util.Random

/**
  * Shuffles integers randomly. Important for generating data with
  *   1) Random distribution
  *   2) Zero duplication (100% cardinality)
  *
  * @note that for shuffling 1 billion integers you'd need 4GB+ of Heap
  *       because the primitive 32bits Ints itself take 4GBs
  *       random shuffling cannot be done lazily !!!
  */
object IntShuffler {

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

  def shuffle(arr: Array[Int]) = {
    def swap(i1: Int, i2: Int) = {
      val tmp = arr(i1)
      arr(i1) = arr(i2)
      arr(i2) = tmp
    }
    for (n <- arr.length to 2 by -1) {
      swap(n - 1, Random.nextInt(n))
    }
    arr
  }
}