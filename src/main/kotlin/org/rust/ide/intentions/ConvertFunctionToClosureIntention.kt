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
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.moveCaretToOffset

class ConvertFunctionToClosureIntention : RsElementBaseIntentionAction<ConvertFunctionToClosureIntention.Context>() {
    override fun getText(): String = RsBundle.message("intention.name.convert.function.to.closure")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(val targetFunction: RsFunction)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val possibleTarget = element.contextStrict<RsFunction>() ?: return null

        val availabilityRange = TextRange(
            possibleTarget.fn.startOffset,
            possibleTarget.retType?.endOffset ?: possibleTarget.valueParameterList?.endOffset ?: return null
        )
        if (element.startOffset !in availabilityRange) return null

        // if we found one, we need to check if it's a child of another function, which would mean it's an local function
        if (possibleTarget.contextStrict<RsFunction>() == null) return null
        if (possibleTarget.typeParameterList != null) return null
        if (!PsiModificationUtil.canReplace(possibleTarget)) return null

        return Context(possibleTarget)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) = doInvoke(project, editor, ctx.targetFunction)

    fun doInvoke(project: Project, editor: Editor?, function: RsFunction) {
        val factory = RsPsiFactory(project)

        val parametersText = function.rawValueParameters.joinToString(", ", transform = RsValueParameter::getText)
        val returnText = function.retType?.text ?: ""

        val bodyText = function.block?.text ?: return

        val lambda = factory.createLambda("|$parametersText| $returnText $bodyText")
        val declaration = factory.createLetDeclaration(function.identifier.text, lambda)

        val replaced = function.replace(declaration) as RsLetDecl
        replaced.semicolon?.endOffset?.let {
            editor?.moveCaretToOffset(replaced, it)
        }
    }
}
