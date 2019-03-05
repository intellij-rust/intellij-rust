/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import com.google.common.math.LongMath.*
import org.rust.lang.core.dfa.LongRangeSet.Companion.empty
import org.rust.lang.core.dfa.LongRangeSet.Companion.point
import org.rust.lang.core.dfa.LongRangeSet.Companion.unknown
import org.rust.lang.core.dfa.value.DfaConstValue
import org.rust.lang.core.dfa.value.DfaFactMapValue
import org.rust.lang.core.dfa.value.DfaValue
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger
import java.util.*
import java.util.stream.LongStream
import kotlin.NoSuchElementException
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

sealed class LongRangeSet(val type: TyInteger) {
    val minPossible = type.MIN_POSSIBLE
    val maxPossible = type.MAX_POSSIBLE

    /**
     * Subtracts given set from the current
     *
     * @param other set to subtract
     * @return a new set
     */
    abstract fun subtract(other: LongRangeSet): LongRangeSet

    fun without(value: Long): LongRangeSet = subtract(point(value))

    /**
     * @return true if set is empty
     */
    val isEmpty: Boolean get() = this is Empty

    /**
     * @return true if set is overflow
     */
    val isOverflow: Boolean get() = this === Empty.Overflow

    /**
     * @return true if a division by zero error occurred
     */
    val hasDivisionByZero: Boolean get() = this === Empty.DivisionByZero

    /**
     * @return true if set is empty
     */
    val isUnknown: Boolean get() = this is Unknown

    /**
     * @return true if type is greater than Long
     */
    val isLarge: Boolean get() = isLargeOnTop || isLargeBelow

    val isLargeOnTop: Boolean get() = type.isLargeOnTop

    val isLargeBelow: Boolean get() = type.isLargeBelow

    /**
     * Intersects current set with other
     *
     * @param other other set to intersect with
     * @return a new set
     */
    abstract fun intersect(other: LongRangeSet): LongRangeSet

    /**
     * Merge current set with other
     *
     * @param other other set to merge with
     * @return a new set
     */
    open fun unite(other: LongRangeSet): LongRangeSet = when {
        other.isUnknown -> other
        other.isEmpty || other === this -> this
        this in other -> other
        else -> {
            val range = allRange
            // TODO optimize
            range.subtract(range.subtract(this).intersect(range.subtract(other)))
        }
    }

    fun less(other: LongRangeSet): LongRangeSet = other.fromRelation(ComparisonOp.LT).intersect(this)
    fun lessOrEqual(other: LongRangeSet): LongRangeSet = other.fromRelation(ComparisonOp.LTEQ).intersect(this)
    fun more(other: LongRangeSet): LongRangeSet = other.fromRelation(ComparisonOp.GT).intersect(this)
    fun moreOrEqual(other: LongRangeSet): LongRangeSet = other.fromRelation(ComparisonOp.GTEQ).intersect(this)

    /**
     * @return a minimal value contained in the set
     * @throws NoSuchElementException if set is empty
     */
    abstract val min: Long

    /**
     * @return a maximal value contained in the set
     * @throws NoSuchElementException if set is empty
     */
    abstract val max: Long

    /**
     * Checks if current set and other set have at least one common element
     *
     * @param other other set to check whether intersection exists
     * @return true if this set intersects other set
     */
    abstract fun intersects(other: LongRangeSet): Boolean

    /**
     * Checks whether current set contains given value
     *
     * @param value value to find
     * @return true if current set contains given value
     */
    abstract operator fun contains(value: Long): Boolean

    /**
     * Checks whether current set contains all the values from other set
     *
     * @param other a sub-set candidate
     * @return true if current set contains all the values from other
     */
    abstract operator fun contains(other: LongRangeSet): Boolean

    /**
     * Creates a new set which contains all possible values satisfying given predicate regarding the current set.
     *
     *
     * E.g. if current set is {0..10} and relation is "GT", then result will be {1..RsRange.MAX_VALUE} (values which can be greater than
     * some value from the current set)
     *
     * @param relation relation to be applied to current set
     * @return new set or UNKNOWN if relation is unsupported
     */
    fun fromRelation(relation: BoolOp): LongRangeSet {
        when (relation) {
            EqualityOp.EQ -> return this
            EqualityOp.EXCLEQ -> return allRange.subtract(this)
        }
        return if (isUnknown || isEmpty) this
        else when (relation) {
            ComparisonOp.GT -> {
                val min = min
                if (min == maxPossible)
                    if (isLargeOnTop) unknown() else empty()
                else range(min + 1, maxPossible)
            }
            ComparisonOp.GTEQ -> range(min, maxPossible)
            ComparisonOp.LTEQ -> range(minPossible, max)
            ComparisonOp.LT -> {
                val max = max
                if (max == minPossible)
                    if (isLargeBelow) unknown() else empty()
                else range(minPossible, max - 1)
            }
            else -> unknown()
        }
    }

    /**
     * Performs a supported binary operation from op (defined in [OverloadableBinaryOperator]).
     *
     * @param op  a op which corresponds to the operation
     * @param right  a right-hand operand
     * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements);
     * or UNKNOWN if the supplied op is not supported.
     */
    fun binOpFromToken(op: OverloadableBinaryOperator, right: LongRangeSet): LongRangeSet = when (op) {
        ArithmeticOp.ADD, ArithmeticAssignmentOp.PLUSEQ -> plus(right)
        ArithmeticOp.SUB, ArithmeticAssignmentOp.MINUSEQ -> minus(right)
        ArithmeticOp.REM, ArithmeticAssignmentOp.REMEQ -> rem(right)
        ArithmeticOp.DIV, ArithmeticAssignmentOp.DIVEQ -> div(right)
        ArithmeticOp.MUL, ArithmeticAssignmentOp.MULEQ -> times(right)
        is BoolOp -> {
            val (l, r) = compare(op, right)
            l.unite(r)
        }
        else -> unknown()
    }

    fun compare(op: BoolOp, right: LongRangeSet): Pair<LongRangeSet, LongRangeSet> = when (op) {
        EqualityOp.EQ -> {
            val res = intersect(right)
            res to res
        }
        EqualityOp.EXCLEQ -> subtract(right) to right.subtract(this)
        ComparisonOp.LT -> less(right) to right.more(this)
        ComparisonOp.LTEQ -> lessOrEqual(right) to right.moreOrEqual(this)
        ComparisonOp.GT -> more(right) to right.less(this)
        ComparisonOp.GTEQ -> moreOrEqual(right) to right.lessOrEqual(this)
        else -> unknown() to unknown()
    }


    /**
     * Returns a range which represents all the possible values after applying [Math.abs] or [Math.abs]
     * to the values from this set
     *
     * @return a new range
     */
    abstract val abs: LongRangeSet

    val invert: LongRangeSet get() = allRange.subtract(this)

    /**
     * Returns a range which represents all the possible values after applying unary minus
     * to the values from this set
     *
     * @return a new range
     */
    abstract operator fun unaryMinus(): LongRangeSet


    /**
     * Returns a range which represents all the possible values after performing an addition between any value from this range
     * and any value from other range. The resulting range may contain some more values which cannot be produced by addition.
     * Guaranteed to be commutative.
     *
     * @return a new range
     */
    abstract operator fun plus(other: LongRangeSet): LongRangeSet

    /**
     * Returns a range which represents all the possible values after performing an addition between any value from this range
     * and any value from other range. The resulting range may contain some more values which cannot be produced by addition.
     *
     * @return a new range
     */
    operator fun minus(other: LongRangeSet): LongRangeSet {
        return plus(-other)
    }

    //TODO make abstract ?
    operator fun times(other: LongRangeSet): LongRangeSet {
        if (isEmpty || other.isEmpty) return empty(other)
        if (isZero) return this
        if (other.isZero) return other
        if (isUnknown || other.isUnknown) return unknown()

        val left = without(0).asRanges
        val right = other.without(0).asRanges

        var result = if (0 in this || 0 in other) point(0) else empty()
        for (leftIndex in left.indices step 2) {
            for (rightIndex in right.indices step 2) {
                val part = times(left[leftIndex], left[leftIndex + 1], right[rightIndex], right[rightIndex + 1])
                if (part.isUnknown) return part
                result = result.unite(part)
            }
        }
        return result.overflowIfEmpty()
    }

    private fun times(lhsMin: Long, lhsMax: Long, rhsMin: Long, rhsMax: Long): LongRangeSet = if (lhsMax > 0) {
        if (rhsMin > 0) {
            val left = checkedMultiplyOrNull(lhsMin, rhsMin)
            val right = checkedMultiplyOrNull(lhsMax, rhsMax)
            rangeWithOverflowCheck(left, right)
        } else {
            val left = checkedMultiplyOrNull(lhsMax, rhsMin)
            val right = checkedMultiplyOrNull(lhsMin, rhsMax)
            rangeWithOverflowCheck(left, right)
        }
    } else {
        if (rhsMin > 0) times(rhsMin, rhsMax, lhsMin, lhsMax)
        else {
            val left = checkedMultiplyOrNull(lhsMax, rhsMax)
            val right = checkedMultiplyOrNull(lhsMin, rhsMin)
            rangeWithOverflowCheck(left, right)
        }
    }

    private fun rangeWithOverflowCheck(from: Long?, to: Long?) = when {
        from == null || to == null -> when {
            to == null && isLargeOnTop || from == null && isLargeBelow -> unknown()
            from == null && to == null -> empty(true)
            else -> LongRangeSet.range(from ?: minPossible, to ?: maxPossible, type)
        }
        from > maxPossible || to < minPossible -> empty(true)
        else -> LongRangeSet.range(overflowCorrection(from), overflowCorrection(to), type)
    }

    /**
     * Returns a range which represents all the possible values after applying `x / y` operation for
     * all `x` from this set and for all `y` from the divisor set. The resulting set may contain
     * some more values. Division by zero yields an empty set of possible results.
     *
     * @param divisor other division results do not depend on the resulting type.
     * @return a new range
     */
    operator fun div(divisor: LongRangeSet): LongRangeSet {
        if (divisor.isEmpty || divisor.isZero) return Empty.DivisionByZero
        if (isEmpty) return empty(divisor)
        if (isZero) return this
        if (isUnknown || divisor.isUnknown) return unknown()

        val left = splitAtZero(asRanges)
        val right = divisor.without(0).asRanges

        var result = empty()
        for (i in left.indices step 2) {
            for (j in right.indices step 2) {
                result = result.unite(divide(left[i], left[i + 1], right[j], right[j + 1]))
            }
        }
        return if (result.isEmpty) empty(true) else result
    }

    private fun divide(dividendMin: Long, dividendMax: Long, divisorMin: Long, divisorMax: Long): LongRangeSet = when {
        dividendMin >= 0 -> if (divisorMin > 0) range(dividendMin / divisorMax, dividendMax / divisorMin)
        else range(dividendMax / divisorMax, dividendMin / divisorMin)
        divisorMin > 0 -> range(dividendMin / divisorMin, dividendMax / divisorMax)
        else -> if (dividendMin == minPossible && divisorMax == -1L) {
            if (divisorMin == -1L) -range(dividendMin, dividendMax)
            else range(dividendMin / divisorMin, dividendMin / (divisorMax - 1)).unite(
                if (dividendMax == minPossible) if (isLargeOnTop) unknown() else empty(true)
                else range(dividendMax / divisorMin, (dividendMin + 1) / divisorMax)
            )
        } else range(dividendMax / divisorMin, dividendMin / divisorMax)
    }

    /**
     * Returns a range which represents all the possible values after applying `x % y` operation for
     * all `x` from this set and for all `y` from the divisor set. The resulting set may contain
     * some more values. Division by zero yields an empty set of possible results.
     *
     * @param divisor divisor set to divide by
     * @return a new range
     */
    abstract operator fun rem(divisor: LongRangeSet): LongRangeSet

    /**
     * Returns a stream of all values from this range. Be careful: could be huge
     *
     * @return a new stream
     */
    abstract val stream: LongStream

    abstract val asRanges: LongArray

    fun to(type: Ty?): LongRangeSet? = fromType(type)?.intersect(this)

    protected open val fixRange: LongRangeSet get() = type.toRange().intersect(this)

    protected fun fromRanges(ranges: LongArray, bound: Int): LongRangeSet = fromRanges(ranges, bound, type)

    protected fun point(value: Long): LongRangeSet = point(value, type)

    protected fun set(ranges: LongArray): LongRangeSet = set(ranges, type)

    protected fun range(from: Long, to: Long): LongRangeSet = range(from, to, type)

    protected val allRange get() = all(type)

    //    protected fun typeEquals(other: LongRangeSet) = type == other.type
    companion object {
        /**
         * Creates a set containing single value
         *
         * @param `value` constant to create a set from or null if type is large
         * @param `type` type of constant
         * @return new LongRangeSet or null if constant type is unsupported
         */
        fun fromConstant(value: Long?, type: Ty): LongRangeSet? = if (type is TyInteger) when (value) {
            is Long -> pointOrOverflowOrUnknown(value, type)
            null -> unknown()
            else -> null
        }
        else null

        fun fromDfaValue(value: DfaValue): LongRangeSet? = when (value) {
            is DfaFactMapValue -> value[DfaFactType.RANGE]
            is DfaConstValue -> fromConstant(value.value as? Long, value.type)
            else -> null
        }

        fun fromRanges(ranges: LongArray, bound: Int, type: TyInteger): LongRangeSet = when (bound) {
            0 -> empty()
            2 -> range(ranges[0], ranges[1], type)
            else -> set(ranges.copyOfRange(0, bound), type)
        }

        fun fromType(type: Ty?): LongRangeSet? = (type as? TyInteger)?.toRange()

        /**
         * Creates a new set which contains all the numbers between from (inclusive) and to (inclusive)
         *
         * @param from lower bound
         * @param to upper bound (must be greater or equal to `from`)
         * @return a new LongRangeSet
         */
        fun range(from: Long, to: Long, type: TyInteger = TyInteger.I64): LongRangeSet = if (from == to) Point(from, type) else Range(from, to, type)

        fun point(value: Long, type: TyInteger = TyInteger.I64): LongRangeSet = Point(value, type)

        fun set(ranges: LongArray, type: TyInteger = TyInteger.I64): LongRangeSet = RangeSet(ranges, type)

        fun empty(overflow: Boolean = false): LongRangeSet = if (overflow) Empty.Overflow else Empty.EmptyRange

        fun unknown(): LongRangeSet = Unknown

        fun all(type: TyInteger = TyInteger.I64): LongRangeSet = type.toRange()
    }
}

sealed class Empty : LongRangeSet(TyInteger.I64) {
    override fun subtract(other: LongRangeSet): LongRangeSet = this

    override fun intersect(other: LongRangeSet): LongRangeSet = this

    override fun unite(other: LongRangeSet): LongRangeSet = other

    override val min: Long get() = throw NoSuchElementException()

    override val max: Long get() = throw NoSuchElementException()

    override fun intersects(other: LongRangeSet): Boolean = false

    override operator fun contains(value: Long): Boolean = false

    override operator fun contains(other: LongRangeSet): Boolean = other.isEmpty

    override val abs: LongRangeSet get() = this

    override operator fun unaryMinus(): LongRangeSet = this

    override operator fun plus(other: LongRangeSet): LongRangeSet = empty(other)

    override operator fun rem(divisor: LongRangeSet): LongRangeSet = if (divisor.isEmpty || divisor.isZero) Empty.DivisionByZero else this

    override val stream: LongStream get() = LongStream.empty()

    override val asRanges: LongArray get() = throw UnsupportedOperationException()

    override fun hashCode(): Int = 2154231

    override fun equals(other: Any?): Boolean = other === this

    override fun toString(): String = "{}"

    object Overflow : Empty() {
        override fun toString(): String = "{!}"
    }

    object DivisionByZero : Empty() {
        override fun toString(): String = "{z}"
    }

    object EmptyRange : Empty()
}

object Unknown : LongRangeSet(TyInteger.I64) {
    override fun subtract(other: LongRangeSet): LongRangeSet = this

    override fun intersect(other: LongRangeSet): LongRangeSet = if (other.isEmpty) other else this

    override fun unite(other: LongRangeSet): LongRangeSet = this

    override val min: Long get() = throw NoSuchElementException()

    override val max: Long get() = throw NoSuchElementException()

    override fun intersects(other: LongRangeSet): Boolean = !other.isEmpty

    override operator fun contains(value: Long): Boolean = true

    override operator fun contains(other: LongRangeSet): Boolean = true

    override val abs: LongRangeSet get() = this

    override operator fun unaryMinus(): LongRangeSet = this

    override operator fun plus(other: LongRangeSet): LongRangeSet = if (other.isEmpty) other else this

    override operator fun rem(divisor: LongRangeSet): LongRangeSet = if (divisor.isEmpty || divisor.isZero) Empty.DivisionByZero else this

    override val stream: LongStream get() = throw UnsupportedOperationException()

    override val asRanges: LongArray get() = throw UnsupportedOperationException()

    override fun hashCode(): Int = 2154230

    override fun equals(other: Any?): Boolean = other === this

    override fun toString(): String = "{?}"
}

class Point(val value: Long, type: TyInteger) : LongRangeSet(type) {
    override fun subtract(other: LongRangeSet): LongRangeSet = when {
        this in other -> empty()
        else -> this
    }

    override fun intersect(other: LongRangeSet): LongRangeSet = when {
        other.isUnknown -> other
        value in other -> this
        else -> empty()
    }

    override val min: Long get() = value

    override val max: Long get() = value

    override fun intersects(other: LongRangeSet): Boolean = value in other

    override operator fun contains(value: Long): Boolean = this.value == value

    override operator fun contains(other: LongRangeSet): Boolean = other.isEmpty || other.isUnknown || this == other

    override val abs: LongRangeSet
        get() = when {
            value >= 0 -> this
            value == minPossible -> empty(true)
            else -> point(-value)
        }

    override operator fun unaryMinus(): LongRangeSet = if (value == minPossible)
        if (isLargeOnTop) unknown() else empty(true)
    else point(-value)

    override operator fun plus(other: LongRangeSet): LongRangeSet = when {
        other.isEmpty || other.isUnknown -> other
        other is Point -> {
            val result = checkedAddOrNull(value, other.value)
            when (result) {
                null -> if (isLargeOnTop && value > 0 || isLargeBelow && value < 0) unknown() else empty(true)
                overflowCorrection(result) -> point(result)
                else -> empty(true)
            }
        }
        else -> other + this
    }

    override operator fun rem(divisor: LongRangeSet): LongRangeSet {
        if (divisor.isUnknown) return divisor
        if (divisor.isEmpty || divisor.isZero) return Empty.DivisionByZero
        if (value == 0L) return this
        val divisor = divisor.without(0)
        if (divisor is Point) {
            return point(value % divisor.value)
        }
        if (value != minPossible) {
            val abs = value.absoluteValue
            if (!divisor.intersects(range(-abs, abs))) {
                // like 10 % [15..20] == 10 regardless on exact divisor value
                return this
            }
        }
        var divisorMin = divisor.min
        if (divisorMin == minPossible) {
            divisorMin += 1
        }
        val max = max(0, max(divisorMin.absoluteValue, divisor.max.absoluteValue) - 1)
        return if (value < 0) range(max(value, -max), 0)
        else range(0, min(value, max)) // 10 % [-4..7] is [0..6], but 10 % [-30..30] is [0..10]
    }

    override val stream: LongStream get() = LongStream.of(value)

    override val asRanges: LongArray get() = longArrayOf(value, value)

    override fun hashCode(): Int = value.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other === this) true
        else other is Point && value == other.value

    override fun toString(): String = "{$value}"
}

class Range(val from: Long, val to: Long, type: TyInteger) : LongRangeSet(type) {
    init {
        if (from >= to) throw IllegalArgumentException("$from >= $to")
    }

    override fun subtract(other: LongRangeSet): LongRangeSet {
        if (other === this || this in other) return empty()
        if (other.isEmpty) return this
        if (other is Point) {
            val value = other.value
            return when {
                value < this.from || value > this.to -> this
                value == this.from -> range(this.from + 1, this.to)
                value == this.to -> range(this.from, this.to - 1)
                else -> set(longArrayOf(this.from, value - 1, value + 1, this.to))
            }
        }
        if (other is Range) {
            val from = other.from
            val to = other.to
            return when {
                to < this.from || from > this.to -> this
                from <= this.from && to >= this.to -> empty()
                from > this.from && to < this.to -> set(longArrayOf(this.from, from - 1, to + 1, this.to))
                from <= this.from -> range(to + 1, this.to)
                to >= this.to -> range(this.from, from - 1)
                else -> throw InternalError("Impossible: $this:$other")
            }
        }
        val ranges = (other as RangeSet).ranges
        var result: LongRangeSet = this
        for (i in ranges.indices step 2) {
            result = result.subtract(range(ranges[i], ranges[i + 1]))
            if (result.isEmpty) return result
        }
        return result
    }

    override fun intersect(other: LongRangeSet): LongRangeSet {
        if (other === this) return this
        if (other.isEmpty || other.isUnknown) return other
        if (other is Point) {
            return other.intersect(this)
        }
        if (other is Range) {
            var from = other.from
            var to = other.to
            if (from <= this.from && to >= this.to) return this
            if (from >= this.from && to <= this.to) return other
            if (from < this.from) {
                from = this.from
            }
            if (to > this.to) {
                to = this.to
            }
            return if (from <= to) range(from, to) else empty()
        }
        val ranges = (other as RangeSet).ranges
        val result = LongArray(ranges.size)
        var index = 0
        for (i in ranges.indices step 2) {
            val part = intersect(range(ranges[i], ranges[i + 1]))
            if (part.isEmpty) continue
            val res = part.asRanges
            System.arraycopy(res, 0, result, index, res.size)
            index += res.size
        }
        return fromRanges(result, index)
    }

    override val min: Long get() = from

    override val max: Long get() = to

    override fun intersects(other: LongRangeSet): Boolean = when {
        other.isEmpty -> false
        other.isUnknown -> true
        other is RangeSet -> other.intersects(this)
        else -> this.to >= other.min && this.from <= other.max
    }

    override operator fun contains(value: Long): Boolean = value in from..to

    override operator fun contains(other: LongRangeSet): Boolean = other.isEmpty || other.isUnknown || other.min >= from && other.max <= to

    override val abs: LongRangeSet
        get() {
            if (from >= 0) return this
            val minValue = minPossible
            var low = from
            var hi = to
            if (low <= minValue) {
                low = minValue + 1
            }
            if (this.to <= 0) {
                hi = -low
                low = -this.to
            } else {
                hi = max(-low, hi)
                low = 0
            }
            return range(low, hi)
        }

    override operator fun unaryMinus(): LongRangeSet {
        val minValue = minPossible
        return if (this.from == minValue)
            if (this.to == maxPossible) type.toRange()
            else range(-this.to, -(minValue + 1))
        else range(-this.to, -this.from)
    }

    override operator fun plus(other: LongRangeSet): LongRangeSet {
        if (other.isEmpty || other.isUnknown) return other
        if (other is Point || other is Range /*|| other is RangeSet TODO optimization && other.ranges.size > 6*/) {
            return plus(from, to, other.min, other.max)
        }
        val ranges = other.asRanges
        var result = empty()
        for (i in ranges.indices step 2) {
            result = result.unite(plus(this.from, this.to, ranges[i], ranges[i + 1]))
            if (result.isUnknown) return result
        }
        return result.overflowIfEmpty()
    }

    private fun plus(from1: Long, to1: Long, from2: Long, to2: Long): LongRangeSet {
        val from = checkedAddOrNull(from1, from2)
        val to = checkedAddOrNull(to1, to2)
        return when {
            from == null || to == null -> if (to == null && isLargeOnTop || from == null && isLargeBelow) unknown() else range(from
                ?: minPossible, to ?: maxPossible)
            from > maxPossible || to < minPossible -> empty(true)
            else -> range(overflowCorrection(from), overflowCorrection(to))
        }
    }

    override operator fun rem(divisor: LongRangeSet): LongRangeSet {
        if (divisor.isUnknown) return divisor
        if (divisor.isEmpty || divisor.isZero) return Empty.DivisionByZero
        val divisor = divisor.without(0)
        if (divisor is Point && divisor.value == minPossible) {
            return if (contains(minPossible)) subtract(divisor).unite(point(0)) else this
        }
        if (divisor.contains(minPossible)) return possibleMod()
        val min = divisor.min
        val max = divisor.max
        val maxDivisor = max(min.absoluteValue, max.absoluteValue)
        val minDivisor = if (min > 0) min else if (max < 0) max.absoluteValue else 0
        return if (minPossible <= -minDivisor && !intersects(range(minPossible, -minDivisor)) && !intersects(range(minDivisor, maxPossible))) this
        else possibleMod().intersect(range(-maxDivisor + 1, maxDivisor - 1))
    }

    private fun possibleMod(): LongRangeSet = when {
        contains(0) -> this
        min > 0 -> range(0, max)
        else -> range(min, 0)
    }

    override val stream: LongStream get() = LongStream.rangeClosed(from, to)

    override val asRanges: LongArray get() = longArrayOf(from, to)

    override fun hashCode(): Int = from.hashCode() * 1337 + to.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other === this) true else other is Range && from == other.from && to == other.to

    override fun toString(): String = "{${toString(from, to)}}"

    private object RangeValuesHolder {
        val I8 = rangeFromType(TyInteger.I8)
        val U8 = rangeFromType(TyInteger.U8)
        val I16 = rangeFromType(TyInteger.I16)
        val U16 = rangeFromType(TyInteger.U16)
        val I32 = rangeFromType(TyInteger.I32)
        val U32 = rangeFromType(TyInteger.U32)
        val I64 = rangeFromType(TyInteger.I64)
        val U64 = rangeFromType(TyInteger.U64)
        val I128 = rangeFromType(TyInteger.I128)
        val U128 = rangeFromType(TyInteger.U128)
        val ISize = rangeFromType(TyInteger.ISize)
        val USize = rangeFromType(TyInteger.USize)
    }

    companion object {
        val I8 get() = RangeValuesHolder.I8
        val U8 get() = RangeValuesHolder.U8
        val I16 get() = RangeValuesHolder.I16
        val U16 get() = RangeValuesHolder.U16
        val I32 get() = RangeValuesHolder.I32
        val U32 get() = RangeValuesHolder.U32
        val I64 get() = RangeValuesHolder.I64
        val U64 get() = RangeValuesHolder.U64
        val I128 get() = RangeValuesHolder.I128
        val U128 get() = RangeValuesHolder.U128
        val ISize get() = RangeValuesHolder.ISize
        val USize get() = RangeValuesHolder.USize
    }
}

private fun toString(from: Long, to: Long): String =
    if (from == to) from.toString() else "$from${if (to - from == 1L) ", " else ".."}$to"

val TyInteger.MIN_POSSIBLE: Long
    get() =
        when (this) {
            is TyInteger.I8 -> Byte.MIN_VALUE.toLong()
            is TyInteger.U8 -> 0L
            is TyInteger.I16 -> Short.MIN_VALUE.toLong()
            is TyInteger.U16 -> 0L
            is TyInteger.I32 -> Int.MIN_VALUE.toLong()
            is TyInteger.U32 -> 0L
            is TyInteger.I64 -> Long.MIN_VALUE
            is TyInteger.U64 -> 0L
            is TyInteger.I128 -> Long.MIN_VALUE
            is TyInteger.U128 -> 0L
            is TyInteger.ISize -> TyInteger.I64.MIN_POSSIBLE
            is TyInteger.USize -> TyInteger.U64.MIN_POSSIBLE
        }

val TyInteger.MAX_POSSIBLE: Long
    get() =
        when (this) {
            is TyInteger.I8 -> Byte.MAX_VALUE.toLong()
            is TyInteger.U8 -> 255L
            is TyInteger.I16 -> Short.MAX_VALUE.toLong()
            is TyInteger.U16 -> 65_535L
            is TyInteger.I32 -> Int.MAX_VALUE.toLong()
            is TyInteger.U32 -> 4_294_967_295L
            is TyInteger.I64 -> Long.MAX_VALUE
            is TyInteger.U64 -> Long.MAX_VALUE
            is TyInteger.I128 -> Long.MAX_VALUE
            is TyInteger.U128 -> Long.MAX_VALUE
            is TyInteger.ISize -> TyInteger.I64.MAX_POSSIBLE
            is TyInteger.USize -> TyInteger.U64.MAX_POSSIBLE
        }

private fun rangeFromType(type: TyInteger): Range = with(type) { Range(MIN_POSSIBLE, MAX_POSSIBLE, this) }

class RangeSet(val ranges: LongArray, type: TyInteger) : LongRangeSet(type) {
    init {
        if (ranges.size < 4 || ranges.size % 2 != 0) {
            // 0 ranges = Empty; 1 range = Range
            throw IllegalArgumentException("Bad length: ${ranges.size} $ranges")
        }
        for (i in ranges.indices step 2) {
            if (ranges[i + 1] < ranges[i]) throw IllegalArgumentException("Bad sub-range #${i / 2} $ranges")
            if (i > 0 && (ranges[i - 1] == Long.MAX_VALUE || 1 + ranges[i - 1] > ranges[i])) throw IllegalArgumentException(
                "Bad sub-ranges #${(i / 2 - 1)} and #${i / 2} $ranges"
            )
        }
    }

    override fun subtract(other: LongRangeSet): LongRangeSet {
        if (other.isEmpty) return this
        if (other.isUnknown || other === this) return empty()
        val result = LongArray(ranges.size + other.asRanges.size)
        var index = 0
        for (i in ranges.indices step 2) {
            val res = range(ranges[i], ranges[i + 1]).subtract(other)
            if (res.isEmpty) continue
            val ranges = res.asRanges
            System.arraycopy(ranges, 0, result, index, ranges.size)
            index += ranges.size
        }
        return fromRanges(result, index)
    }

    override fun intersect(other: LongRangeSet): LongRangeSet = when {
        other === this -> this
        other.isEmpty || other.isUnknown -> other
        other is Point || other is Range -> other.intersect(this)
        else -> subtract(allRange.subtract(other))
    }

    override val min: Long get() = ranges.first()

    override val max: Long get() = ranges.last()

    override fun intersects(other: LongRangeSet): Boolean {
        if (other.isEmpty) return false
        if (other.isUnknown) return true
        if (other is Point) return other.value in this
        val otherRanges = other.asRanges
        var a = 0
        var b = 0
        while (true) {
            val aFrom = ranges[a]
            val aTo = ranges[a + 1]
            val bFrom = otherRanges[b]
            val bTo = otherRanges[b + 1]
            if (aFrom <= bTo && bFrom <= aTo) return true
            if (aFrom > bTo) {
                b += 2
                if (b >= otherRanges.size) return false
            } else {
                a += 2
                if (a >= ranges.size) return false
            }
        }
    }

    override operator fun contains(value: Long): Boolean {
        for (i in ranges.indices step 2) {
            if (value >= ranges[i] && value <= ranges[i + 1]) return true
        }
        return false
    }

    override operator fun contains(other: LongRangeSet): Boolean {
        if (other.isEmpty || other.isUnknown || other === this) return true
        if (other is Point) return contains(other.value)
        var result = other
        for (i in ranges.indices step 2) {
            result = result.subtract(range(ranges[i], ranges[i + 1]))
            if (result.isEmpty) return true
        }
        return false
    }

    override val abs: LongRangeSet
        get() {
            var result = empty()
            for (i in ranges.indices step 2) {
                result = result.unite(range(ranges[i], ranges[i + 1]).abs)
            }
            return result.overflowIfEmpty()
        }

    override operator fun unaryMinus(): LongRangeSet {
        var result = empty()
        for (i in ranges.indices step 2) {
            result = result.unite(-range(ranges[i], ranges[i + 1]))
        }
        return result.overflowIfEmpty()
    }

    override operator fun plus(other: LongRangeSet): LongRangeSet {
        if (other.isUnknown) return other
        //TODO optimization
//        if (ranges.size > 6) return range(min, max) + other
        var result = empty()
        for (i in ranges.indices step 2) {
            result = result.unite(range(ranges[i], ranges[i + 1]) + other)
            if (result.isUnknown) return result
        }
        return result
    }

    override operator fun rem(divisor: LongRangeSet): LongRangeSet {
        if (divisor.isEmpty || divisor.isZero) return Empty.DivisionByZero
        if (divisor.isUnknown) return divisor
        val divisor = divisor.without(0)
        var result = empty()
        for (i in ranges.indices step 2) {
            result = result.unite(range(ranges[i], ranges[i + 1]) % divisor)
        }
        return result.overflowIfEmpty()
    }

    override val stream: LongStream
        get() = LongStream.range(0, (ranges.size / 2).toLong()).mapToObj {
            LongStream.rangeClosed(
                ranges[(it * 2).toInt()],
                ranges[(it * 2 + 1).toInt()]
            )
        }.reduce(LongStream::concat).orElseGet(LongStream::empty)

    override val asRanges: LongArray get() = ranges

    override fun hashCode(): Int = Arrays.hashCode(ranges)

    override fun equals(other: Any?): Boolean =
        if (other === this) true
        else other is RangeSet && ranges.contentEquals(other.ranges)

    override fun toString(): String = buildString {
        append("{")
        for (i in ranges.indices step 2) {
            if (i > 0) append(", ")
            append(toString(ranges[i], ranges[i + 1]))
        }
        append("}")
    }
}

private fun TyInteger.toRange(): Range = when (this) {
    TyInteger.I8 -> Range.I8
    TyInteger.U8 -> Range.U8
    TyInteger.I16 -> Range.I16
    TyInteger.U16 -> Range.U16
    TyInteger.I32 -> Range.I32
    TyInteger.U32 -> Range.U32
    TyInteger.I64 -> Range.I64
    TyInteger.U64 -> Range.U64
    TyInteger.I128 -> Range.I128
    TyInteger.U128 -> Range.U128
    TyInteger.ISize -> Range.ISize
    TyInteger.USize -> Range.USize
}

private val TyInteger.size: Long
    get() = when (this) {
        TyInteger.I8 -> 8
        TyInteger.U8 -> 8
        TyInteger.I16 -> 16
        TyInteger.U16 -> 16
        TyInteger.I32 -> 32
        TyInteger.U32 -> 32
        TyInteger.I64 -> 64
        TyInteger.U64 -> 64
        TyInteger.I128 -> 128
        TyInteger.U128 -> 128
        TyInteger.ISize -> 64
        TyInteger.USize -> 64
    }

private val LongRangeSet.isZero: Boolean get() = this is Point && value == 0L

private val TyInteger.isLargeOnTop: Boolean
    get() = when (this) {
        TyInteger.U64 -> true
        TyInteger.I128 -> true
        TyInteger.U128 -> true
        TyInteger.USize -> true
        else -> false
    }

private val TyInteger.isLargeBelow: Boolean
    get() = when (this) {
        TyInteger.I128 -> true
        else -> false
    }

private val TyInteger.isLarge: Boolean get() = this.isLargeOnTop || this.isLargeBelow

private fun Long.overflowCorrection(type: TyInteger): Long {
    val maxPossible = type.MAX_POSSIBLE
    val minPossible = type.MIN_POSSIBLE
    return when {
        this > maxPossible -> maxPossible
        this < minPossible -> minPossible
        else -> this
    }
}

private fun pointOrOverflowOrUnknown(value: Long, type: TyInteger): LongRangeSet = with(type) {
    when {
        value < MIN_POSSIBLE -> if (isLargeBelow) unknown() else empty(true)
        value > MAX_POSSIBLE -> if (isLargeOnTop) unknown() else empty(true)
        else -> point(value, type)
    }
}

private fun splitAtZero(ranges: LongArray): LongArray {
    for (i in ranges.indices step 2) {
        if (ranges[i] < 0 && ranges[i + 1] >= 0) {
            val result = LongArray(ranges.size + 2)
            System.arraycopy(ranges, 0, result, 0, i + 1)
            result[i + 1] = -1
            System.arraycopy(ranges, i + 1, result, i + 3, ranges.size - i - 1)
            return result
        }
    }
    return ranges
}

private fun LongRangeSet.empty(other: LongRangeSet): LongRangeSet = empty(isOverflow || other.isOverflow)
private fun LongRangeSet.overflowIfEmpty(): LongRangeSet = if (isEmpty) empty(true) else this

private val ComparisonOp.mirror: ComparisonOp
    get() = when (this) {
        ComparisonOp.GT -> ComparisonOp.LT
        ComparisonOp.LT -> ComparisonOp.GT
        ComparisonOp.GTEQ -> ComparisonOp.LTEQ
        ComparisonOp.LTEQ -> ComparisonOp.GTEQ
    }

private fun LongRangeSet.overflowCorrection(value: Long): Long = when {
    value <= minPossible -> minPossible
    value >= maxPossible -> maxPossible
    else -> value
}

fun checkedAddOrNull(a: Long, b: Long): Long? = try {
    checkedAdd(a, b)
} catch (e: ArithmeticException) {
    null
}

fun checkedSubOrNull(a: Long, b: Long): Long? = try {
    checkedSubtract(a, b)
} catch (e: ArithmeticException) {
    null
}

fun checkedMultiplyOrNull(a: Long, b: Long): Long? = try {
    checkedMultiply(a, b)
} catch (e: ArithmeticException) {
    null
}

fun checkedModOrNull(a: Long, b: Long): Long? = try {
    a % b
} catch (e: ArithmeticException) {
    null
}

fun checkedDivOrNull(a: Long, b: Long): Long? = try {
    if (a == Long.MIN_VALUE && b == -1L) null else a / b
} catch (e: ArithmeticException) {
    null
}
