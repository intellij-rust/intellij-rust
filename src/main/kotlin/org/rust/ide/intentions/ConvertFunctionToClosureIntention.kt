/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant

class ConvertFunctionToClosureIntention : RsElementBaseIntentionAction<ConvertFunctionToClosureIntention.Context>() {

    override fun getText(): String = "Convert function to closure"

    data class Context(
        val targetFunction: RsFunction,
    )

    private fun getFunctionForElementInSignature(element: PsiElement): RsFunction? {
        for (el in element.ancestors) {
            return when (el) {
                is RsFunction -> el
                is RsBlock -> return null
                else -> continue
            }
        }

        return null
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // We try to find a function declaration
        val possibleTarget = getFunctionForElementInSignature(element) ?: return null

        // if we found one, we need to check if it's a child of another function, which would mean it's an local function
        possibleTarget.ancestorStrict<RsFunction>() ?: return null

        return Context(possibleTarget)
    }

    override fun getFamilyName(): String = "Convert between local function and closure"

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val parametersText = ctx.targetFunction.valueParameters.joinToString(", ", transform = RsValueParameter::getText)
        val returnText = ctx.targetFunction.retType?.text ?: ""

        val bodyText = ctx.targetFunction.body?.text ?: return

        val lambda = factory.createLambda("|$parametersText| $returnText $bodyText")
        val declaration = factory.createLetDeclaration(ctx.targetFunction.identifier.text, lambda)

        val replaced = ctx.targetFunction.replace(declaration) as RsLetDecl
        replaced.semicolon?.endOffset?.let {
            editor.caretModel.moveToOffset(it)
        }
    }

}
