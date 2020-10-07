/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.TextRange
import com.intellij.util.SmartList
import org.rust.stdext.optimizeList
import org.rust.stdext.readVarInt
import org.rust.stdext.writeVarInt
import java.io.DataInput
import java.io.DataOutput

/**
 * Must provide [equals] method because it is used to track changes in the macro expansion mechanism
 */
@Suppress("DataClassPrivateConstructor")
data class RangeMap private constructor(val ranges: List<MappedTextRange>) {

    fun isEmpty(): Boolean = ranges.isEmpty()

    fun mapOffsetFromExpansionToCallBody(offset: Int): Int? {
        return ranges.singleOrNull { range ->
            offset >= range.dstOffset && offset < range.dstEndOffset
        }?.let { range ->
            range.srcOffset + (offset - range.dstOffset)
        }
    }

    fun mapOffsetFromCallBodyToExpansion(offset: Int): List<Int> {
        return ranges.filter { range ->
            offset >= range.srcOffset && offset < range.srcEndOffset
        }.map { range ->
            range.dstOffset + (offset - range.srcOffset)
        }
    }

    private fun mapTextRangeFromExpansionToCallBody(toMap: TextRange): List<MappedTextRange> {
        return ranges.mapNotNull { it.dstIntersection(toMap) }
    }

    fun mapMappedTextRangeFromExpansionToCallBody(toMap: MappedTextRange): List<MappedTextRange> {
        return mapTextRangeFromExpansionToCallBody(TextRange(toMap.srcOffset, toMap.srcEndOffset)).map { mapped ->
            MappedTextRange(
                mapped.srcOffset,
                toMap.dstOffset + (mapped.dstOffset - toMap.srcOffset),
                mapped.length
            )
        }
    }

    fun mapAll(other: RangeMap): RangeMap {
        return RangeMap(other.ranges.flatMap(::mapMappedTextRangeFromExpansionToCallBody))
    }

    fun writeTo(data: DataOutput) {
        data.writeInt(ranges.size)
        ranges.forEach {
            data.writeMappedTextRange(it)
        }
    }

    companion object {
        val EMPTY: RangeMap = RangeMap(emptyList())

        fun readFrom(data: DataInput): RangeMap {
            val size = data.readInt()
            val ranges = (0 until size).map { data.readMappedTextRange() }
            return RangeMap(ranges)
        }

        fun from(ranges: SmartList<MappedTextRange>): RangeMap {
            return RangeMap(ranges.optimizeList())
        }
    }
}

private fun DataInput.readMappedTextRange(): MappedTextRange = MappedTextRange(
    readVarInt(),
    readVarInt(),
    readVarInt()
)

private fun DataOutput.writeMappedTextRange(range: MappedTextRange) {
    writeVarInt(range.srcOffset)
    writeVarInt(range.dstOffset)
    writeVarInt(range.length)
}

data class MappedTextRange(
    val srcOffset: Int,
    val dstOffset: Int,
    val length: Int
) {
    init {
        require(srcOffset >= 0) { "srcOffset should be >= 0; got: $srcOffset" }
        require(dstOffset >= 0) { "dstOffset should be >= 0; got: $dstOffset" }
        require(length > 0) { "length should be grater than 0; got: $length" }
    }
}

val MappedTextRange.srcEndOffset: Int get() = srcOffset + length
val MappedTextRange.dstEndOffset: Int get() = dstOffset + length

val MappedTextRange.srcRange: TextRange get() = TextRange(srcOffset, srcOffset + length)

fun MappedTextRange.srcShiftLeft(delta: Int) = copy(srcOffset = srcOffset - delta)

private fun MappedTextRange.dstIntersection(range: TextRange): MappedTextRange? {
    val newDstStart = Math.max(dstOffset, range.startOffset)
    val newDstEnd = Math.min(dstEndOffset, range.endOffset)
    return if (newDstStart < newDstEnd) {
        val srcDelta = newDstStart - dstOffset
        MappedTextRange(
            srcOffset = srcOffset + srcDelta,
            dstOffset = newDstStart,
            length = newDstEnd - newDstStart
        )
    } else {
        null
    }
}
