/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.AddRemainingArmsFix
import org.rust.ide.inspections.fixes.AddWildcardArmFix
import org.rust.ide.utils.checkMatch.checkExhaustive
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class MatchPostfixTemplate(provider: RsPostfixTemplateProvider) :
    PostfixTemplateWithExpressionSelector(
        null,
        "match",
        "match expr {...}",
        RsExprParentsSelector(),
        provider
    ) {

    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        if (expression !is RsExpr) return

        val project = expression.project
        val factory = RsPsiFactory(project)

        val exprText = if (expression is RsStructLiteral) "(${expression.text})" else expression.text
        val emptyMatch = factory.createExpression("match $exprText {}")
        val matchExpr = expression.replace(emptyMatch) as RsMatchExpr

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val patterns = matchExpr.checkExhaustive().orEmpty()
        val fix = if (patterns.isEmpty()) {
            AddWildcardArmFix(matchExpr)
        } else {
            AddRemainingArmsFix(matchExpr, patterns)
        }
        fix.invoke(project, matchExpr.containingFile, matchExpr, matchExpr)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val matchBody = matchExpr.matchBody ?: return
        val wildPatterns = matchBody.descendantsOfType<RsPatWild>()

        if (wildPatterns.isEmpty()) {
            val arm = matchBody.matchArmList.firstOrNull() ?: return
            val blockExpr = arm.expr as? RsBlockExpr ?: return
            editor.caretModel.moveToOffset(blockExpr.block.lbrace.textOffset + 1)
        } else {
            editor.buildAndRunTemplate(matchBody, wildPatterns.map { it.createSmartPointer() })
        }
    }
}
