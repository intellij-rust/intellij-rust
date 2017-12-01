/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.macroName

fun expandMacro(call: RsMacroCall): List<ExpansionResult>? {
    val context = call.context as? RsElement ?: return null
    val result = when {
        call.macroName == "lazy_static" -> expandLazyStatic(call)?.let { listOf(it) }
        else -> {
            val def = call.reference.resolve() as? RsMacroDefinition ?: return null
            val project = context.project
            val expander = MacroExpander(project)
            expander.expandMacro(def, call)
        }
    }
    result?.forEach { it.setContext(context) }
    return result
}
