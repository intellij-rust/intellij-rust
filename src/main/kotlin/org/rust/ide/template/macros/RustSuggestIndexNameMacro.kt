package org.rust.ide.template.macros

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.innerDeclarations

class RustSuggestIndexNameMacro : MacroBase("rustSuggestIndexName", "rustSuggestIndexName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        if (params.isNotEmpty()) return null
        val pivot = context.psiElementAtStartOffset?.parentOfType<RustCompositeElement>() ?: return null
        val pats = getPatBindingNamesVisibleAt(pivot)
        return ('i'..'z')
            .map(Char::toString)
            .find { it !in pats }
            ?.let(::TextResult)
    }
}

private fun getPatBindingNamesVisibleAt(pivot: RustCompositeElement): Set<String> =
    innerDeclarations(pivot,
        // we are only interested in local scopes
        stop = { scope -> scope is RustItemElement }
    )
        .mapNotNull { (it.element as? RsPatBinding)?.name }
        .toHashSet()
