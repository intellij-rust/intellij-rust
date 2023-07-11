/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.PsiModificationUtil
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyFunctionBase
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.moveCaretToOffset

class ConvertClosureToFunctionIntention : RsElementBaseIntentionAction<ConvertClosureToFunctionIntention.Context>() {
    override fun getText(): String = RsBundle.message("intention.name.convert.closure.to.function")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val letDecl: RsLetDecl,
        val lambda: RsLambdaExpr,
    )

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
        if (!PsiModificationUtil.canReplace(possibleTarget)) return null

        return Context(possibleTarget, lambdaExpr)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val letBidingName = (ctx.letDecl.pat as? RsPatIdent)?.patBinding?.nameIdentifier?.text
        val useDefaultName = letBidingName == null
        val targetFunctionName = letBidingName ?: "func"

        val fnType = ctx.lambda.type as? TyFunctionBase ?: return
        val parametersText = ctx.lambda.valueParameters.zip(fnType.paramTypes).joinToString(", ") { (pat, paramType) ->
            val patText = pat.patText ?: "_"
            val type = paramType.renderInsertionSafe()
            "$patText: $type"
        }

        val lambdaRetTypePsi = ctx.lambda.retType
        val lambdaRetTy = fnType.retType

        val returnText = if (lambdaRetTypePsi != null) {
            lambdaRetTypePsi.text
        } else if (lambdaRetTy != TyUnknown && lambdaRetTy !is TyUnit) {
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

        val placeholders = findPlaceholders(replaced)

        // in case we auto-generated a function name, we encourage the user to change it by running a template on the replacement
        if (useDefaultName || placeholders.isNotEmpty()) {
            val placeholderElements = mutableListOf<PsiElement>()
            if (useDefaultName) {
                placeholderElements.add(replaced.identifier)
            }
            placeholderElements += placeholders
            editor.buildAndRunTemplate(replaced, placeholderElements)
        } else {
            editor.moveCaretToOffset(replaced, replaced.endOffset)
        }
    }

    private fun findPlaceholders(replaced: RsFunction): List<PsiElement> {
        val parameters = replaced.valueParameterList ?: return emptyList()
        val wildcardPats = parameters.descendantsOfType<RsPatWild>()
        val wildcardPaths = parameters.descendantsOfType<RsPath>().filter { it.path?.text == "_" }
        return wildcardPats + wildcardPaths
    }
}
