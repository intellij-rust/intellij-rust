/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import org.rust.cargo.project.settings.rustSettings
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.rustStructureModificationTracker

private val NULL_RESULT: CachedValueProvider.Result<List<ExpansionResult>?> =
    CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT)

fun expandMacro(call: RsMacroCall): CachedValueProvider.Result<List<ExpansionResult>?> {
    val context = call.context as? RsElement ?: return NULL_RESULT
    return when {
        call.macroName == "lazy_static" -> {
            val result = expandLazyStatic(call)?.let { listOf(it) }
            result?.forEach {
                it.setContext(context)
                it.setExpandedFrom(call)
            }
            CachedValueProvider.Result.create(result, call.containingFile)
        }
        else -> {
            val project = context.project
            if (!project.rustSettings.expandMacros) return NULL_RESULT
            val def = call.reference.resolve() as? RsMacro ?: return NULL_RESULT
            val expander = MacroExpander(project)
            val result = expander.expandMacro(def, call)
            result?.forEach {
                it.setContext(context)
                it.setExpandedFrom(call)
            }
            CachedValueProvider.Result.create(result, project.rustStructureModificationTracker)
        }
    }
}
