/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.rust.ide.presentation.render
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class ConvertClosureToFunctionIntention : RsElementBaseIntentionAction<ConvertClosureToFunctionIntention.Context>() {

    override fun getText(): String = "Convert closure to function"
    override fun getFamilyName(): String = "Convert between local function and closure"

    data class Context(
        val assignment: RsLetDecl,
        val lambda: RsLambdaExpr,
    )

    private fun getLetDeclarationForElementInSignature(element: PsiElement): RsLetDecl? {
        if (element.text == ";") {
            return null
        }

        for (el in element.ancestors) {
            return when (el) {
                is RsLetDecl -> el
                is RsValueArgumentList -> el.ancestorStrict()
                is RsBlock -> return null
                else -> continue
            }
        }

        return null
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // We try to find a let declaration
        val possibleTarget = element.ancestorStrict<RsLetDecl>() ?: return null

        // The assignment of the let declaration should be a lambda to be a valid target
        if (possibleTarget.expr !is RsLambdaExpr) {
            return null
        }

        val signatureTarget = getLetDeclarationForElementInSignature(element) ?: return null

        return Context(signatureTarget, signatureTarget.expr as RsLambdaExpr)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        var targetName = ctx.assignment.pat?.text

        var isTemplate = if (targetName == null || !isValidRustVariableIdentifier(targetName)) {
            targetName = "func"

            true
        } else {
            false
        }

        val valueParameters = ctx.lambda.valueParameters
        val (parametersText, parameterPlaceholders) = this.createParameterList(factory, valueParameters)
        if (parameterPlaceholders.isNotEmpty()) {
            isTemplate = true
        }
        val lambdaExpr = ctx.lambda.expr
        val returnText = if (ctx.lambda.retType != null) {
            ctx.lambda.retType!!.text
        } else if (lambdaExpr != null && lambdaExpr.type != TyUnknown && lambdaExpr.type != TyUnit){
            "-> ${lambdaExpr.type.render()}"
        } else {
            ""
        }

        val body = if (lambdaExpr is RsBlockExpr) {
            lambdaExpr.text
        } else {
            "{ ${lambdaExpr?.text} }"
        }

        val function = factory.createFunction("fn $targetName($parametersText) $returnText $body")
        val replaced = ctx.assignment.replace(function) as RsFunction

        val identifier = replaced.identifier

        // in case we auto-generated a function name, we encourage the user to change it by running a template on the replacement
        if (isTemplate) {
            val placeholderElements = mutableListOf<SmartPsiElementPointer<PsiElement>>()
            if (targetName == "func") {
                placeholderElements.add(identifier.createSmartPointer())
            }
            placeholderElements.addAll(replaced.descendantsOfType<RsTypeReference>().filter {
                it.type == TyUnit
            }.map { it.createSmartPointer() })
                editor.buildAndRunTemplate(replaced, placeholderElements)

        } else {
            editor.caretModel.moveToOffset(replaced.endOffset)
        }
    }

    private fun createParameterList(factory: RsPsiFactory, valueParameters: List<RsValueParameter>): Pair<String, List<RsTypeReference>> {
        val placeholders = mutableListOf<RsTypeReference>()
        val newParams = valueParameters.map { param ->
            if (param.typeReference == null) {
                val colon = param.addAfter(factory.createColon(), param.pat)
                val placeholder = param.addAfter(factory.createType("()"), colon) as RsTypeReference
                placeholders.add(placeholder)

                param
            } else {
                param
            }
        }.joinToString(", ") {
            it.text
        }

        return Pair(newParams, placeholders)
    }
}
