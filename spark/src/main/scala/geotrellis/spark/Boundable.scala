package geotrellis.spark

import org.apache.spark.rdd.RDD

/**
 * This type class marks K as point that can be bounded in space.
 * It is used to construct bounding hypercube for a set of Ks.
 *
 * The bounds must be calculated by taking min/max of each component dimension of K.
 * Consequently the result may be neither a nor b, but a new value.
 */
trait Boundable[K] extends Serializable {
  def minBound(p1: K, p2: K): K
  
  def maxBound(p1: K, p2: K): K
  
  def include(p: K, bounds: KeyBounds[K]): KeyBounds[K] = {    
    KeyBounds(
      minBound(bounds.minKey, p),
      maxBound(bounds.maxKey, p))
  }

  def includes(p: K, bounds: KeyBounds[K]): Boolean = {
    bounds == KeyBounds(
      minBound(bounds.minKey, p),
      maxBound(bounds.maxKey, p))
  }

  def combine(b1: KeyBounds[K], b2: KeyBounds[K]): KeyBounds[K] = {
    KeyBounds(
      minBound(b1.minKey, b2.minKey),
      maxBound(b1.maxKey, b2.maxKey))
  }

  def intersect(b1: KeyBounds[K], b2: KeyBounds[K]): Option[KeyBounds[K]] = {
    val kb = KeyBounds(
      maxBound(b1.minKey, b2.minKey),
      minBound(b1.maxKey, b2.maxKey))

    // Intersection may not exist
    if (minBound(kb.minKey, kb.maxKey) == kb.minKey)
      Some(kb)
    else
      None
  }

  def intersects(b1: KeyBounds[K], b2: KeyBounds[K]): Boolean = {
    intersect(b1,b2).isDefined
  }

  def getKeyBounds(rdd: RDD[(K, V)] forSome {type V}): KeyBounds[K]
}
