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
                    RangeMap(MappedTextRange(srcOffset, 0, text.length))
                )
            } else {
                EMPTY
            }
        }
    }
}

class MutableMappedText private constructor(
    private val sb: StringBuilder,
    private val ranges: MutableList<MappedTextRange> = mutableListOf()
) {
    constructor(capacity: Int) : this(StringBuilder(capacity))

    val length: Int get() = sb.length
    val text: CharSequence get() = sb

    fun appendUnmapped(text: CharSequence) {
        sb.append(text)
    }

    fun appendMapped(text: CharSequence, srcOffset: Int) {
        if (text.isNotEmpty()) {
            ranges.mergeAdd(MappedTextRange(srcOffset, sb.length, text.length))
            sb.append(text)
        }
    }

    fun toMappedText(): MappedText = MappedText(sb.toString(), RangeMap(SmartList(ranges)))

    override fun toString(): String {
        return sb.toString()
    }
}
