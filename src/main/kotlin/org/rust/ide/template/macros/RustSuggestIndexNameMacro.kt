package org.rust.ide.template.macros

import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.macro.MacroBase
import com.intellij.psi.PsiDocumentManager
import org.rust.ide.template.RustContextType

class RustSuggestIndexNameMacro : MacroBase("rustSuggestIndexName", "rustSuggestIndexName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        if (params.isNotEmpty()) return null

        val project = context.project
        val offset = context.startOffset
        val document = context.editor?.document ?: return null
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val place = file.findElementAt(offset)
        val pats = getPatBindingNamesVisibleAt(place)

        return ('i'..'z')
            .map { it.toString() }
            .find { it !in pats }
            ?.let { TextResult(it) }
    }

    override fun isAcceptableInContext(context: TemplateContextType?): Boolean = context is RustContextType
}
