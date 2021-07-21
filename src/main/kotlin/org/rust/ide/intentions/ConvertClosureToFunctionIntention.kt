/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

class ConvertClosureToFunctionIntention : RsElementBaseIntentionAction<ConvertClosureToFunctionIntention.Context>() {

    override fun getText(): String = "Convert closure to function"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // We try to find a let declaration
        val possibleTarget = element.ancestorStrict<RsLetDecl>() ?: return null

        // The assignment of the let declaration should be a lambda to be a valid target
        val lambdaExpr = possibleTarget.expr as? RsLambdaExpr ?: return null

        val availabilityRange = TextRange(
            possibleTarget.let.startOffset,
            lambdaExpr.retType?.endOffset ?: lambdaExpr.valueParameterList.endOffset
        )
        if (element.startOffset !in availabilityRange) return null

        return Context(possibleTarget, lambdaExpr)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val letBidingName = (ctx.letDecl.pat as? RsPatIdent)?.patBinding?.nameIdentifier?.text
        val useDefaultName = letBidingName == null
        val targetFunctionName = letBidingName ?: "func"

        val (parametersText, parameterPlaceholders) = createParameterList(factory, ctx.lambda.valueParameters)

        val lambdaRetTypePsi = ctx.lambda.retType
        val lambdaRetTy = (ctx.lambda.type as? TyFunction)?.retType

        val returnText = if (lambdaRetTypePsi != null) {
            lambdaRetTypePsi.text
        } else if (lambdaRetTy != null && lambdaRetTy != TyUnknown && lambdaRetTy !is TyUnit) {
            "-> ${lambdaRetTy.renderInsertionSafe()}"
        } else {
            ""
        }

        val lambdaExpr = ctx.lambda.expr
        val body = if (lambdaExpr is RsBlockExpr) {
            lambdaExpr.text
        } else {
            "{ ${lambdaExpr?.text} }"
        }

        val function = factory.createFunction("fn $targetFunctionName($parametersText) $returnText $body")
        val replaced = ctx.letDecl.replace(function) as RsFunction

        // in case we auto-generated a function name, we encourage the user to change it by running a template on the replacement
        if (useDefaultName || parameterPlaceholders.isNotEmpty()) {
            val placeholderElements = mutableListOf<SmartPsiElementPointer<PsiElement>>()
            if (useDefaultName) {
                placeholderElements.add(replaced.identifier.createSmartPointer())
            }
            replaced.valueParameterList?.valueParameterList.orEmpty().forEachIndexed { i, param ->
                if (i in parameterPlaceholders) {
                    param.typeReference?.let { placeholderElements += it.createSmartPointer() }
                }
            }
            editor.buildAndRunTemplate(replaced, placeholderElements)
        } else {
            editor.caretModel.moveToOffset(replaced.endOffset)
        }
    }

    private fun createParameterList(
        factory: RsPsiFactory,
        valueParameters: List<RsValueParameter>
    ): Pair<String, Set<Int>> {
        val placeholders = mutableSetOf<Int>()
        valueParameters.forEachIndexed { i, param ->
            if (param.typeReference == null) {
                val type = param.pat?.type ?: TyUnknown

                val colon = param.addAfter(factory.createColon(), param.pat)

                if (type != TyUnknown) {
                    param.addAfter(factory.createType(type.renderInsertionSafe()), colon) as RsTypeReference
                } else {
                    param.addAfter(factory.createType("()"), colon) as RsTypeReference
                    placeholders.add(i)
                }
            }
        }
        val newParams = valueParameters.joinToString(", ") { it.text }

        return Pair(newParams, placeholders)
    }

    data class Context(
        val letDecl: RsLetDecl,
        val lambda: RsLambdaExpr,
    )
}
