/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import org.jetbrains.annotations.PropertyKey
import org.rust.openapiext.RsPathManager

val PP_PATH: String get() = RsPathManager.prettyPrintersDir().toString()
const val LLDB_LOOKUP: String = "lldb_lookup"
const val GDB_LOOKUP: String = "gdb_formatters.gdb_lookup"

enum class LLDBRenderers(@PropertyKey(resourceBundle = BUNDLE) private val descriptionKey: String) {
    NONE("rust.debugger.renderers.none.item"),
    COMPILER("rust.debugger.renderers.compiler.item"),
    BUNDLED("rust.debugger.renderers.bundled.item");

    override fun toString(): String = RsDebuggerBundle.message(descriptionKey)

    companion object {
        val DEFAULT: LLDBRenderers = BUNDLED
        fun fromIndex(index: Int): LLDBRenderers = values().getOrElse(index) { DEFAULT }
    }
}

enum class GDBRenderers(@PropertyKey(resourceBundle = BUNDLE) private val descriptionKey: String) {
    NONE("rust.debugger.renderers.none.item"),
    COMPILER("rust.debugger.renderers.compiler.item"),
    BUNDLED("rust.debugger.renderers.bundled.item");

    override fun toString(): String = RsDebuggerBundle.message(descriptionKey)

    companion object {
        val DEFAULT: GDBRenderers = BUNDLED
        fun fromIndex(index: Int): GDBRenderers = values().getOrElse(index) { DEFAULT }
    }
}
