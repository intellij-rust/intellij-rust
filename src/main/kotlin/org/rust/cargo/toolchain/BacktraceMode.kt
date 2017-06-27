/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

enum class BacktraceMode(val index: Int, val title: String) {
    NO(0, "No"),
    SHORT(1, "Short"),
    FULL(2, "Full");

    override fun toString(): String = title

    companion object {
        val DEFAULT = FULL
        fun fromIndex(index: Int) = BacktraceMode.values().find { it.index == index } ?: DEFAULT
    }
}
