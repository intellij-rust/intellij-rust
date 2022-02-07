/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.ide.refactoring.suggestedNames
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.completion.RsLookupElementProperties.KeywordKind
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.OR
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonWhitespaceSibling
import org.rust.lang.core.psi.ext.leftSiblings
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psiElement
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.createSmartPointer

object RsLambdaExprCompletionProvider : RsCompletionProvider() {

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement<PsiElement>()
            .withSuperParent(2, psiElement<RsPathExpr>())

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position.safeGetOriginalOrSelf()
        val expr = element.parentOfType<RsExpr>() ?: return
        val exprExpectedType = expr.expectedType ?: return
        val paramTypes = expr.implLookup.asTyFunction(exprExpectedType)?.value?.paramTypes ?: return

        val params = suggestedParams(paramTypes, expr)
        val start = if (expr.leftSiblings.filter { it !is PsiErrorElement }.firstOrNull()?.elementType == OR) {
            ""
        } else {
            "|"
        }
        val text = "$start$params| {}"

        result.addElement(
            LookupElementBuilder
                .create(text)
                .bold()
                .withPresentableText("|| {}")
                .withInsertHandler { ctx, _ -> handleInsert(ctx) }
                .toKeywordElement(KeywordKind.LAMBDA_EXPR)
        )
    }
}

private fun handleInsert(ctx: InsertionContext) {
    val lambda = ctx.getElementOfType<RsLambdaExpr>() ?: return
    val pats = lambda.descendantsOfType<RsPatIdent>().toList()

    val lambdaPtr = lambda.createSmartPointer()

    val template = ctx.editor.newTemplateBuilder(lambda) ?: return
    for (pat in pats) {
        template.replaceElement(pat)
    }

    val expr = lambda.expr
    if (expr != null) {
        template.replaceElement(expr)
    }
    template.withFinishResultListener {
        val element = lambdaPtr.element
        if (element != null) {
            val blockExpr = element.expr as? RsBlockExpr ?: return@withFinishResultListener
            if (blockExpr.block.lbrace.getNextNonWhitespaceSibling() != blockExpr.block.rbrace) {
                return@withFinishResultListener
            }

            val offset = blockExpr.block.lbrace.startOffset
            ctx.editor.caretModel.moveToOffset(offset + 1)
        }
    }
    template.runInline()
}

private fun suggestedParams(paramTypes: List<Ty>, contextExpr: RsExpr): String {
    val alreadyGivenNames = mutableSetOf<String>()
    return paramTypes.joinToString { ty ->
        val name = ty.suggestedNames(contextExpr, alreadyGivenNames).default
        alreadyGivenNames += name
        name
    }
}
