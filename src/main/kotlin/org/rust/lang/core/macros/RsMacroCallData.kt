/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.macroBody

class RsMacroCallData(
    val macroBody: MacroCallBody,
    val env: Map<String, String>,
) {

    companion object {
        fun fromPsi(call: RsPossibleMacroCall): RsMacroCallData? {
            val macroBody = call.macroBody ?: return null
            val isCompletion = CompletionUtil.getOriginalElement(call) != null
            val packageEnv = call.containingCargoPackage?.env.orEmpty()
            val env = if (isCompletion) {
                packageEnv + mapOf(
                    "RUST_IDE_PROC_MACRO_COMPLETION" to "1",
                    "RUST_IDE_PROC_MACRO_COMPLETION_DUMMY_IDENTIFIER" to DUMMY_IDENTIFIER_TRIMMED,
                )
            } else {
                packageEnv
            }
            return RsMacroCallData(
                macroBody,
                env,
            )
        }
    }
}
