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

private val NULL_RESULT: CachedValueProvider.Result<List<RsExpandedElement>?> =
    CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT)

fun expandMacro(call: RsMacroCall): CachedValueProvider.Result<List<RsExpandedElement>?> {
    val context = call.context as? RsElement ?: return NULL_RESULT
    val project = context.project
    val expander = MacroExpander(project)
    return when (call.macroName) {
        "lazy_static" -> {
            val result = expandLazyStatic(call)
            setExpansionContext(result, context, call)
            CachedValueProvider.Result.create(result, call.containingFile)
        }
        "include" -> {
            val result = expandInclude(call) ?: return NULL_RESULT
            setExpansionContext(result, context, call)
            CachedValueProvider.Result.create(result, project.rustStructureModificationTracker)
        }
        else -> {
            if (!project.rustSettings.expandMacros) return NULL_RESULT
            val def = call.reference.resolve() as? RsMacro ?: return NULL_RESULT
            val result = expander.expandMacro(def, call)
            setExpansionContext(result, context, call)
            CachedValueProvider.Result.create(result, project.rustStructureModificationTracker)
        }
    }
}

private fun setExpansionContext(result: List<RsExpandedElement>?, context: RsElement, call: RsMacroCall) {
    result?.forEach {
        it.setContext(context)
        it.setExpandedFrom(call)
    }
}
