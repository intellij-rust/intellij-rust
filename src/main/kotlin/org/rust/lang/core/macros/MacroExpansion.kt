/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import org.rust.cargo.project.settings.rustSettings
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.macroName

private val NULL_RESULT: CachedValueProvider.Result<List<ExpansionResult>?> =
    CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT)

fun expandMacro(call: RsMacroCall): CachedValueProvider.Result<List<ExpansionResult>?> {
    val context = call.context as? RsElement ?: return NULL_RESULT
    return when {
        call.macroName == "lazy_static" -> {
            val result = expandLazyStatic(call)?.let { listOf(it) }
            result?.forEach { it.setContext(context) }
            CachedValueProvider.Result.create(result, call.containingFile)
        }
        else -> {
            if (!context.project.rustSettings.expandMacros) return NULL_RESULT
            val def = call.reference.resolve() as? RsMacroDefinition ?: return NULL_RESULT
            val project = context.project
            val expander = MacroExpander(project)
            val result = expander.expandMacro(def, call)
            result?.forEach { it.setContext(context) }
            // We can use a files instead of `MODIFICATION_COUNT`, so cached value will be depends
            // on modification count of these files. See [PsiCachedValue.getTimeStamp]
            CachedValueProvider.Result.create(result, def.containingFile, call.containingFile)
        }
    }
}
