/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.util.NlsContexts.ListItem
import org.rust.openapiext.RsPathManager

val PP_PATH: String get() = RsPathManager.prettyPrintersDir().toString()
const val LLDB_LOOKUP: String = "lldb_lookup"
const val GDB_LOOKUP: String = "gdb_lookup"

enum class LLDBRenderers(@Suppress("UnstableApiUsage") @ListItem private val description: String) {
    NONE("No renderers"),
    COMPILER("Rust compiler's renderers"),
    BUNDLED("Bundled renderers");

    override fun toString(): String = description

    companion object {
        val DEFAULT: LLDBRenderers = BUNDLED
        fun fromIndex(index: Int): LLDBRenderers = values().getOrElse(index) { DEFAULT }
    }
}

enum class GDBRenderers(private val description: String) {
    NONE("No renderers"),
    COMPILER("Rust compiler's renderers"),
    BUNDLED("Bundled renderers");

    override fun toString(): String = description

    companion object {
        val DEFAULT: GDBRenderers = BUNDLED
        fun fromIndex(index: Int): GDBRenderers = values().getOrElse(index) { DEFAULT }
    }
}
