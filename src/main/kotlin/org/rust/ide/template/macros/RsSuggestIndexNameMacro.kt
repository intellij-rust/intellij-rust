/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.macros

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.resolve.processLocalVariables
import java.util.*

class RsSuggestIndexNameMacro : MacroBase("rustSuggestIndexName", "rustSuggestIndexName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        if (params.isNotEmpty()) return null
        val pivot = context.psiElementAtStartOffset?.ancestorStrict<RsElement>() ?: return null
        val pats = getPatBindingNamesVisibleAt(pivot)
        return ('i'..'z')
            .map(Char::toString)
            .find { it !in pats }
            ?.let(::TextResult)
    }
}

private fun getPatBindingNamesVisibleAt(pivot: RsElement): Set<String> {
    val result = HashSet<String>()
    processLocalVariables(pivot) { patBinding ->
        val name = patBinding.name
        if (name != null) {
            result += name
        }
    }
    return result
}
