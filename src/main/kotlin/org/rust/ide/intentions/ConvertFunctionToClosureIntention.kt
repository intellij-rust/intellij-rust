/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.ext.*

class ConvertFunctionToClosureIntention : RsElementBaseIntentionAction<ConvertFunctionToClosureIntention.Context>() {

    override fun getText(): String = "Convert function to closure"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val possibleTarget = element.ancestorStrict<RsFunction>() ?: return null

        val availabilityRange = TextRange(
            possibleTarget.fn.startOffset,
            possibleTarget.retType?.endOffset ?: possibleTarget.valueParameterList?.endOffset ?: return null
        )
        if (element.startOffset !in availabilityRange) return null

        // if we found one, we need to check if it's a child of another function, which would mean it's an local function
        if (possibleTarget.ancestorStrict<RsFunction>() == null) return null
        if (possibleTarget.typeParameterList != null) return null

        return Context(possibleTarget)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val parametersText = ctx.targetFunction.rawValueParameters.joinToString(", ", transform = RsValueParameter::getText)
        val returnText = ctx.targetFunction.retType?.text ?: ""

        val bodyText = ctx.targetFunction.block?.text ?: return

        val lambda = factory.createLambda("|$parametersText| $returnText $bodyText")
        val declaration = factory.createLetDeclaration(ctx.targetFunction.identifier.text, lambda)

        val replaced = ctx.targetFunction.replace(declaration) as RsLetDecl
        replaced.semicolon?.endOffset?.let {
            editor.caretModel.moveToOffset(it)
        }
    }

    data class Context(
        val targetFunction: RsFunction,
    )
}
