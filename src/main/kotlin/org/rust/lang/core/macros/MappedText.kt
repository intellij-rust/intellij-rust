/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.util.SmartList

data class MappedText(val text: String, val ranges: RangeMap) {
    companion object {
        val EMPTY: MappedText = MappedText("", RangeMap.EMPTY)

        fun single(text: String, srcOffset: Int): MappedText {
            return if (text.isNotEmpty()) {
                MappedText(
                    text,
                    RangeMap.from(SmartList(MappedTextRange(srcOffset, 0, text.length)))
                )
            } else {
                EMPTY
            }
        }
    }
}

class MutableMappedText private constructor(
    private val sb: StringBuilder,
    private val initialSrcOffset: Int = 0,
    private val ranges: MutableList<MappedTextRange> = mutableListOf()
): MappedAppendable {
    constructor(capacity: Int) : this(StringBuilder(capacity))

    override fun appendUnmapped(text: CharSequence) {
        sb.append(text)
    }

    override fun appendUnmapped(i: Int) {
        sb.append(i)
    }

    override fun appendMapped(text: CharSequence, srcOffset: Int) {
        if (text.isNotEmpty()) {
            ranges.mergeAdd(MappedTextRange(initialSrcOffset + srcOffset, sb.length, text.length))
            sb.append(text)
        }
    }

    override fun appendMapped(c: Char, srcOffset: Int) {
        ranges.mergeAdd(MappedTextRange(initialSrcOffset + srcOffset, sb.length, 1))
        sb.append(c)
    }

    fun withSrcOffset(srcOffset: Int): MutableMappedText = MutableMappedText(sb, srcOffset, ranges)

    fun toMappedText(): MappedText = MappedText(sb.toString(), RangeMap.from(SmartList(ranges)))

    override fun toString(): String {
        return sb.toString()
    }
}
