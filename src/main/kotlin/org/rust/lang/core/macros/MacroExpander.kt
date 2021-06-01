/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.builtin.BuiltinMacroExpander
import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.errors.MacroExpansionError
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.stdext.RsResult

abstract class MacroExpander<in T: RsMacroData, out E: MacroExpansionError> {
    abstract fun expandMacroAsTextWithErr(def: T, call: RsMacroCallData): RsResult<Pair<CharSequence, RangeMap>, E>
}

/** A macro expander for macro calls like `foo!()` */
class FunctionLikeMacroExpander(
    private val decl: DeclMacroExpander,
    private val proc: ProcMacroExpander,
    private val builtin: BuiltinMacroExpander
) : MacroExpander<RsMacroData, MacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, MacroExpansionError> {
        return when (def) {
            is RsDeclMacroData -> decl.expandMacroAsTextWithErr(def, call)
            is RsProcMacroData -> proc.expandMacroAsTextWithErr(def, call)
            is RsBuiltinMacroData -> builtin.expandMacroAsTextWithErr(def, call)
        }
    }

    companion object {
        fun new(project: Project): FunctionLikeMacroExpander = FunctionLikeMacroExpander(
            DeclMacroExpander(project),
            ProcMacroExpander(project),
            BuiltinMacroExpander(project)
        )
    }
}
