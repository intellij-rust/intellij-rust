/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

/**
 * A simple utility for bitmask creation. Use it like this:
 *
 * ```
 * object Foo: BitFlagsBuilder(Limit.BYTE) {
 *     val BIT_MASK_0 = nextBitMask() // Equivalent to `1 shl 0`
 *     val BIT_MASK_1 = nextBitMask() // Equivalent to `1 shl 1`
 *     val BIT_MASK_2 = nextBitMask() // Equivalent to `1 shl 2`
 *     // ...etc
 * }
 * ```
 */
abstract class BitFlagsBuilder private constructor(private val limit: Limit, startFromBit: Int) {
    protected constructor(limit: Limit) : this(limit, 0)
    protected constructor(prevBuilder: BitFlagsBuilder, limit: Limit) : this(limit, prevBuilder.counter)

    private var counter: Int = startFromBit

    protected fun nextBitMask(): Int {
        val nextBit = counter++
        if (nextBit == limit.bits) error("Bitmask index out of $limit limit!")
        return makeBitMask(nextBit)
    }

    protected enum class Limit(val bits: Int) {
        BYTE(8), INT(32)
    }
}
