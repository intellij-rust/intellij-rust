/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.builtin

import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.*
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err

/**
 * A macro expander for built-in macros like `concat!()` and `stringify!()`
 */
class BuiltinMacroExpander(val project: Project) : MacroExpander<RsBuiltinMacroData, DeclMacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsBuiltinMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, out DeclMacroExpansionError> {
        val macroCallBodyText = call.macroBody ?: return Err(DeclMacroExpansionError.DefSyntax)
        return Err(DeclMacroExpansionError.DefSyntax)
    }

    companion object {
        const val EXPANDER_VERSION = 0
    }
}
