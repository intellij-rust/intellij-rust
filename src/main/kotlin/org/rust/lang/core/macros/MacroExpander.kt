/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.cargo.project.settings.toolchain
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.builtin.BuiltinMacroExpander
import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.errors.MacroExpansionError
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.lang.core.macros.proc.RustcCompatibilityChecker
import org.rust.stdext.RsResult
import java.util.concurrent.CompletableFuture

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
        fun new(crate: Crate): FunctionLikeMacroExpander {
            val project = crate.project
            val toolchain = project.toolchain
            val rustcInfo = crate.cargoProject?.rustcInfo
            val rustcVersion = rustcInfo?.version
            val isRustcCompatible = if (toolchain != null && rustcInfo != null && rustcVersion != null) {
                RustcCompatibilityChecker.getInstance()
                    .isRustcCompatibleWithProcMacroExpander(project, toolchain, rustcInfo, rustcVersion)
            } else {
                CompletableFuture.completedFuture(RsResult.Ok(Unit))
            }
            return FunctionLikeMacroExpander(
                DeclMacroExpander(project),
                ProcMacroExpander(project, toolchain, isRustcCompatible),
                BuiltinMacroExpander(project)
            )
        }
    }
}
