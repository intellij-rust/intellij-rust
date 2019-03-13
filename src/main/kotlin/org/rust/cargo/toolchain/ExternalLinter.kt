/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

enum class ExternalLinter(val title: String) {
    CARGO_CHECK("Cargo Check"),
    CLIPPY("Clippy");

    override fun toString(): String = title

    companion object {
        @JvmField
        val DEFAULT: ExternalLinter = ExternalLinter.CARGO_CHECK

        fun findByName(name: String): ExternalLinter? = ExternalLinter.values().find { it.title == name }
    }
}
