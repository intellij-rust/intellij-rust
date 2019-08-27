/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.utils.addMissingFieldsToStructLiteral
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

open class AddStructFieldsLiteralIntention : RsElementBaseIntentionAction<AddStructFieldsLiteralIntention.Context>() {

    override fun getText() = "Replace .. with actual fields"

    override fun getFamilyName() = text

    data class Context(
        val structLiteral: RsStructLiteral
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        return if (element.elementType == RsElementTypes.DOTDOT && element.context?.parent is RsStructLiteral) {
            Context(element.context?.parent as RsStructLiteral)
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val structLiteral = ctx.structLiteral
        removeDotsAndBaseStruct(structLiteral)
        addMissingFieldsToStructLiteral(RsPsiFactory(project), editor, structLiteral)
    }

    protected fun removeDotsAndBaseStruct(structLiteral: RsStructLiteral) {
        val structLiteralBody = structLiteral.structLiteralBody
        structLiteralBody.dotdot?.getNextNonCommentSibling()?.delete()
        structLiteralBody.dotdot?.delete()
    }
}
