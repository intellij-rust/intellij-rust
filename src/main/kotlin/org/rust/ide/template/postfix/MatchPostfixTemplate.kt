/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.ide.fixes.AddRemainingArmsFix
import org.rust.ide.fixes.AddWildcardArmFix
import org.rust.ide.utils.checkMatch.checkExhaustive
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyStr
import org.rust.lang.core.types.type
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

        val type = expression.type

        val processor = getMatchProcessor(type, expression)

        val match = processor.createMatch(factory, expression)
        val matchExpr = expression.replace(match) as RsMatchExpr

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        processor.normalizeMatch(matchExpr)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val matchBody = matchExpr.matchBody ?: return
        val toBeReplaced = processor.getElementsToReplace(matchBody)

        editor.fillArmsPlaceholders(toBeReplaced, matchExpr)
    }
}

private fun Editor.fillArmsPlaceholders(elementsToReplace: Collection<RsElement>, match: RsMatchExpr) {
    val firstArmBlock = match.matchBody?.matchArmList?.firstOrNull()?.expr as? RsBlockExpr ?: return
    if (elementsToReplace.isEmpty()) {
        moveCaretToMatchArmBlock(this, firstArmBlock)
    } else {
        val firstArmBlockPointer = firstArmBlock.createSmartPointer()
        buildAndRunTemplate(match, elementsToReplace) {
            val restored = firstArmBlockPointer.element ?: return@buildAndRunTemplate
            moveCaretToMatchArmBlock(this, restored)
        }
    }
}

private fun moveCaretToMatchArmBlock(editor: Editor, blockExpr: RsBlockExpr) {
    editor.caretModel.moveToOffset(blockExpr.block.lbrace.textOffset + 1)
}

private fun getMatchProcessor(ty: Ty, context: RsElement): MatchProcessor {
    return when {
        ty is TyAdt && ty.item == context.knownItems.String -> StringMatchProcessor
        ty is TyReference && ty.referenced is TyStr -> StringLikeMatchProcessor()
        else -> GenericMatchProcessor
    }
}

private abstract class MatchProcessor {
    abstract fun createMatch(factory: RsPsiFactory, expression: RsExpr): RsMatchExpr
    open fun normalizeMatch(matchExpr: RsMatchExpr) {}

    open fun getElementsToReplace(matchBody: RsMatchBody): List<RsElement> = emptyList()
}

private object GenericMatchProcessor : MatchProcessor() {
    override fun createMatch(factory: RsPsiFactory, expression: RsExpr): RsMatchExpr {
        val exprText = if (expression is RsStructLiteral) "(${expression.text})" else expression.text
        return factory.createExpression("match $exprText {}") as RsMatchExpr
    }

    override fun normalizeMatch(matchExpr: RsMatchExpr) {
        val patterns = matchExpr.checkExhaustive().orEmpty()
        val fix = if (patterns.isEmpty()) {
            AddWildcardArmFix(matchExpr)
        } else {
            AddRemainingArmsFix(matchExpr, patterns)
        }
        fix.invoke(matchExpr.project, editor = null, matchExpr)
    }

    override fun getElementsToReplace(matchBody: RsMatchBody): List<RsElement> =
        matchBody.descendantsOfType<RsPatWild>().toList()
}

private open class StringLikeMatchProcessor : MatchProcessor() {
    open fun expressionToText(expression: RsExpr): String = expression.text

    override fun createMatch(factory: RsPsiFactory, expression: RsExpr): RsMatchExpr {
        val exprText = expressionToText(expression)
        return factory.createExpression("match $exprText {\n\"\" => {}\n_ => {} }") as RsMatchExpr
    }

    override fun getElementsToReplace(matchBody: RsMatchBody): List<RsElement> =
        listOfNotNull(matchBody.matchArmList.getOrNull(0)?.pat as? RsPatConst)
}

private object StringMatchProcessor : StringLikeMatchProcessor() {
    override fun expressionToText(expression: RsExpr): String = "${expression.text}.as_str()"
}

fun fillMatchArms(match: RsMatchExpr, editor: Editor) {
    GenericMatchProcessor.normalizeMatch(match)
    val elementsToReplace = match.descendantsOfType<RsPatWild>()
    editor.fillArmsPlaceholders(elementsToReplace, match)
}
