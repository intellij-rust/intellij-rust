/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

enum class RustChannel(val index: Int, val channel: String?) {
    DEFAULT(0, null),
    STABLE(1, "stable"),
    BETA(2, "beta"),
    NIGHTLY(3, "nightly"),
    DEV(4, "dev");

    override fun toString(): String = channel ?: "[default]"

    companion object {

        fun fromIndex(index: Int): RustChannel = values().find { it.index == index } ?: DEFAULT

        fun fromPreRelease(releaseSuffix: String): RustChannel = when {
            releaseSuffix.isEmpty() -> STABLE
            releaseSuffix.startsWith("beta") -> BETA
            releaseSuffix.startsWith("nightly") -> NIGHTLY
            releaseSuffix.startsWith("dev") -> DEV
            else -> DEFAULT
        }
    }
}
