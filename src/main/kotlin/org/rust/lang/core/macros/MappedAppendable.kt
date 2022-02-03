/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

interface MappedAppendable {
    fun appendUnmapped(text: CharSequence)
    fun appendUnmapped(i: Int)
    fun appendMapped(text: CharSequence, srcOffset: Int)
    fun appendMapped(c: Char, srcOffset: Int)
}

class MappedAppendableToStringBuilder(private val sb: StringBuilder) : MappedAppendable {
    override fun appendUnmapped(text: CharSequence) {
        sb.append(text)
    }

    override fun appendUnmapped(i: Int) {
        sb.append(i)
    }

    override fun appendMapped(text: CharSequence, srcOffset: Int) {
        sb.append(text)
    }

    override fun appendMapped(c: Char, srcOffset: Int) {
        sb.append(c)
    }
}
