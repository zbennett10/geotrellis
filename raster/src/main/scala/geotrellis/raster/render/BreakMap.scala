package geotrellis.raster.render

import geotrellis.raster._
import geotrellis.util._

import spire.algebra._
import spire.math.Sorting
import spire.std.any._
import spire.syntax.order._

import scala.specialized

// --- //

/** Root element in hierarchy for specifying the type of boundary when classifying colors*/
sealed trait ClassBoundaryType
case object GreaterThan extends ClassBoundaryType
case object GreaterThanOrEqualTo extends ClassBoundaryType
case object LessThan extends ClassBoundaryType
case object LessThanOrEqualTo extends ClassBoundaryType
case object Exact extends ClassBoundaryType

/** A strategy for mapping values via a [[BreakMap]].
  *
  * '''Note:''' Specialized for `Int` and `Double`.
  */
class MapStrategy[@specialized(Int, Double) A](
  val boundary: ClassBoundaryType,
  val noDataValue: A,
  val fallbackValue: A,
  val strict: Boolean
)

/** Helper methods for constructing a [[MapStrategy]]. */
object MapStrategy {
  def int: MapStrategy[Int] = new MapStrategy(LessThanOrEqualTo, 0x00000000, 0x00000000, false)

  def double: MapStrategy[Double] = new MapStrategy(LessThanOrEqualTo, Double.NaN, Double.NaN, false)
}

/** A `Map` which provides specific Binary Search-based ''map'' behaviour
  * with breaks and a break strategy.
  *
  * {{{
  * val bm: BreakMap = ...
  * val t: Tile = ...
  *
  * // Map all the cells of `t` to a target bin value in O(klogn).
  * val newT: Tile = t.mapWith(vm)
  * }}}
  *
  * '''Note:''' `A` and `B` are specialized on `Int` and `Double`.
  */
class BreakMap[
  @specialized(Int, Double) A: Order,
  @specialized(Int, Double) B: Order
](breakMap: Map[A, B], strategy: MapStrategy[B], noDataCheck: A => Boolean) {

  /* A Binary Tree of the mappable values */
  private lazy val vmTree: BTree[(A, B)] = {
    val a: Array[(A, B)] = breakMap.toArray

    Sorting.quickSort(a)

    BTree.fromSortedSeq(a.toIndexedSeq).get
  }

  /* Yield a btree search predicate function based on boundary type options. */
  private val branchPred: (A, BTree[(A, B)]) => Either[Option[BTree[(A, B)]], (A, B)] = {
    strategy.boundary match {
      case LessThan => { (z, tree) => tree match {
        case BTree(v, None, _)    if z < v._1                       => Right(v)
        case BTree(v, Some(l), _) if z < v._1 && z >= l.greatest._1 => Right(v)
        case BTree(v, l, _)       if z < v._1                       => Left(l)
        case BTree(_, _, r)                                         => Left(r)
      }}
      case LessThanOrEqualTo => { (z, tree) => tree match {
        case BTree(v, None, _)    if z <= v._1                      => Right(v)
        case BTree(v, Some(l), _) if z <= v._1 && z > l.greatest._1 => Right(v)
        case BTree(v, l, _)       if z < v._1                       => Left(l)
        case BTree(_, _, r)                                         => Left(r)
      }}
      case Exact => { (z, tree) => tree match { /* Vanilla Binary Search */
        case BTree(v, _, _) if z == v._1 => Right(v)
        case BTree(v, l, _) if z < v._1  => Left(l)
        case BTree(_, _, r)              => Left(r)
      }}
      case GreaterThanOrEqualTo => { (z, tree) => tree match {
        case BTree(v, _, None)    if z >= v._1                    => Right(v)
        case BTree(v, _, Some(r)) if z >= v._1 && z < r.lowest._1 => Right(v)
        case BTree(v, l, _)       if z < v._1                     => Left(l)
        case BTree(_, _, r)                                       => Left(r)
      }}
      case GreaterThan => { (z, tree) => tree match {
        case BTree(v, _, None)    if z > v._1                     => Right(v)
        case BTree(v, _, Some(r)) if z > v._1 && z <= r.lowest._1 => Right(v)
        case BTree(v, l, _)       if z <= v._1                    => Left(l) /* (<=) is correct here! */
        case BTree(_, _, r)                                       => Left(r)
      }}
    }
  }

  def map(z: A): B = {
    if (noDataCheck(z)) {
      strategy.noDataValue
    } else {
      vmTree.searchWith(z, branchPred) match {
        case Some((_, v)) => v
        case None if strategy.strict => sys.error(s"Value ${z} did not have an associated value.")
        case _ => strategy.fallbackValue
      }
    }
  }
}

/** Helper methods for constructing BreakMaps. */
object BreakMap {
  def i2i(m: Map[Int, Int]): BreakMap[Int, Int] =
    new BreakMap(m, MapStrategy.int, { i => isNoData(i) })

  def i2d(m: Map[Int, Double]): BreakMap[Int, Double] =
    new BreakMap(m, MapStrategy.double, { i => isNoData(i) })

  def d2d(m: Map[Double, Double]): BreakMap[Double, Double] =
    new BreakMap(m, MapStrategy.double, { d => isNoData(d) })

  def d2i(m: Map[Double, Int]): BreakMap[Double, Int] =
    new BreakMap(m, MapStrategy.int, { d => isNoData(d) })
}
