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
import org.rust.lang.utils.ReferenceTargetModificationTracker

private val NULL_RESULT: CachedValueProvider.Result<List<RsExpandedElement>?> =
    CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT)

fun expandMacro(call: RsMacroCall): CachedValueProvider.Result<List<RsExpandedElement>?> {
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

            // The cached value will be invalidated in these cases:
            // 1. Some descendant of RsMacroCall PSI is changed
            // 2. Some descendant of RsMacro PSI is changed
            // 3. RsMacroCall starts to refer to another RsMacro definition
            //
            // The interesting case is when macro call or definition is stubbed.
            // In this case PSI modification tracking is not provided. But stubbed
            // PSI is not updated incrementally. I.e. if something is changed in a
            // stubbed file, all its PSI will be completely invalidated and new one built.
            // So there are two possible scenarios:
            // 1. Changed file is a file with a macro call. In this case, the cache
            //    attached to the call will be invalidate because new PSI will be built
            // 2. Changed file is a stubbed file containing a macro definition referred
            //    by some macro call. In this case, the cached expansion of the call will
            //    be invalidated by ReferenceTargetModificationTracker because new PSI
            //    will be built for the macro definition, and the tracker will treat it
            //    as a modification (another resolve result)
            CachedValueProvider.Result.create(
                result,
                call.modificationTracker,
                def.modificationTracker,
                ReferenceTargetModificationTracker.forRustStructureDependentReference(call.reference)
            )
        }
    }
}
