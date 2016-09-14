package org.rust.ide.template.macros

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustSuggestIndexNameMacro : MacroBase("rustSuggestIndexName", "rustSuggestIndexName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        if (params.isNotEmpty()) return null
        val pivot = context.psiElementAtStartOffset?.parentOfType<RustCompositeElement>() ?: return null
        val pats = getPatBindingNamesVisibleAt(pivot)
        return ('i'..'z')
            .map { it.toString() }
            .find { it !in pats }
            ?.let { TextResult(it) }
    }
}

private fun getPatBindingNamesVisibleAt(pivot: RustCompositeElement): Set<String> =
    RustResolveEngine
        .enumerateScopesFor(pivot)
        .takeWhile {
            // we are only interested in local scopes
            if (it is RustItemElement) {
                // workaround diamond inheritance issue
                // (ambiguity between RustItemElement.parent & RustResolveScope.parent)
                val item: RustItemElement = it
                item.parent is RustBlockElement
            } else {
                it !is RustFile
            }
        }
        .flatMap { RustResolveEngine.declarations(it, pivot) }
        .mapNotNull { (it as? RustPatBindingElement)?.name }
        .toHashSet()
