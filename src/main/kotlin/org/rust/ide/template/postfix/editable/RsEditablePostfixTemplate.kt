/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix.editable

import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.rust.ide.template.postfix.RsExprParentsSelector
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsExprStmt

class RsEditablePostfixTemplate(
    templateId: String,
    templateName: String,
    templateText: String,
    example: String,
    expressionTypes: Set<RsPostfixTemplateExpressionCondition>,
    useTopmostExpression: Boolean,
    provider: PostfixTemplateProvider
) : EditablePostfixTemplateWithMultipleExpressions<RsPostfixTemplateExpressionCondition>(
    templateId, templateName, createTemplate(templateText), example, expressionTypes,
    useTopmostExpression, provider
) {
    override fun getExpressions(context: PsiElement, document: Document, offset: Int): MutableList<PsiElement> {
        if (DumbService.getInstance(context.project).isDumb) return mutableListOf()

        val allExpressions = RsExprParentsSelector().getExpressions(context, document, offset)
        val expressions = if (myUseTopmostExpression) {
            val topmostExpr = allExpressions.maxByOrNull { it.textLength } ?: return mutableListOf()
            mutableListOf(topmostExpr)
        } else {
            allExpressions
        }

        // accept expression of any type if list is empty (it's default state)
        if (myExpressionConditions.isEmpty() && context is RsExpr)
            return expressions.toMutableList()

        return ContainerUtil.filter(expressions, Conditions.and({ e: PsiElement ->
            (PSI_ERROR_FILTER.value(e) && e is RsExpr && e.textRange.endOffset == offset)
        }, expressionCompositeCondition))
    }

    override fun getTopmostExpression(element: PsiElement): PsiElement {
        return if (element.parent is RsExprStmt) element.parent else element
    }

    override fun isBuiltin(): Boolean = false

    companion object {
        private val PSI_ERROR_FILTER =
            Condition { element: PsiElement? -> element != null && !PsiTreeUtil.hasErrorElements(element) }

        fun createTemplate(templateText: String): TemplateImpl {
            val template = TemplateImpl("fakeKey", templateText, "")
            template.isToReformat = false
            template.parseSegments()

            // turn segments (words surrounded by '$' char) into variables
            for (i in 0 until template.segmentsCount) {
                val segmentName = template.getSegmentName(i)
                val internalName = segmentName == "EXPR" || segmentName == TemplateImpl.ARG || segmentName in TemplateImpl.INTERNAL_VARS_SET
                if (!internalName)
                    template.addVariable(segmentName, TextExpression(segmentName), true)
            }

            return template
        }
    }
}
