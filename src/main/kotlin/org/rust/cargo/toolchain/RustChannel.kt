/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

enum class RustChannel(val index: Int, val title: String, val rustupArgument: String?) {
    DEFAULT(0, "[Default]", null),
    STABLE(1, "Stable", "stable"),
    BETA(2, "Beta", "beta"),
    NIGHTLY(3, "Nightly", "nightly");

    override fun toString(): String = title

    companion object {
        fun fromIndex(index: Int) = RustChannel.values().find { it.index == index } ?: RustChannel.DEFAULT
    }
}
