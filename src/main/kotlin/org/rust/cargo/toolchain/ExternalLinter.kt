/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import org.jetbrains.annotations.PropertyKey
import org.rust.BUNDLE
import org.rust.RsBundle

enum class ExternalLinter(@PropertyKey(resourceBundle = BUNDLE) val titleKey: String) {
    CARGO_CHECK("rust.external.linter.cargo.check.item"),
    CLIPPY("rust.external.linter.clippy.item");

    val title: String get() = RsBundle.message(titleKey)

    override fun toString(): String = title

    companion object {
        @JvmField
        val DEFAULT: ExternalLinter = CARGO_CHECK
    }
}
