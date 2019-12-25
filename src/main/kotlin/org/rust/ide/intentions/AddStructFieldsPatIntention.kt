/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.utils.expandStructFields
import org.rust.ide.utils.expandTupleStructFields
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementType

class AddStructFieldsPatIntention : RsElementBaseIntentionAction<AddStructFieldsPatIntention.Context>() {
    override fun getText() = "Replace .. with actual fields"

    override fun getFamilyName() = text

    data class Context(
        val structBody: RsPat
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        if (element.elementType != RsElementTypes.DOTDOT) return null
        val restPat = element.context as? RsPatRest ?: return null
        val context = restPat.context as? RsPat
        return if (context is RsPatStruct || context is RsPatTupleStruct) {
            Context(context)
        } else {
            null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val structBody = ctx.structBody
        if (structBody is RsPatStruct) {
            expandStructFields(factory, structBody)
        } else if (structBody is RsPatTupleStruct) {
            expandTupleStructFields(factory, editor, structBody)
        }
    }
}
