/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.builtin

import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.MacroExpander
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.macros.RsBuiltinMacroData
import org.rust.lang.core.macros.RsMacroCallData
import org.rust.lang.core.macros.errors.BuiltinMacroExpansionError
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err

/**
 * A macro expander for built-in macros like `concat!()` and `stringify!()`
 */
class BuiltinMacroExpander(val project: Project) : MacroExpander<RsBuiltinMacroData, BuiltinMacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsBuiltinMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, BuiltinMacroExpansionError> {
        return Err(BuiltinMacroExpansionError)
    }

    companion object {
        const val EXPANDER_VERSION = 0
    }
}
