/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.utils.PsiModificationUtil
import org.rust.ide.utils.expandStructFields
import org.rust.ide.utils.expandTupleStructFields
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementType

class AddStructFieldsPatIntention : RsElementBaseIntentionAction<AddStructFieldsPatIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.replace.with.actual.fields")

    override fun getFamilyName() = text

    data class Context(
        val structPat: RsPat
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        if (element.elementType != RsElementTypes.DOTDOT) return null
        val restPat = element.context as? RsPatRest ?: return null
        val pat = restPat.context as? RsPat
        if (pat !is RsPatStruct && pat !is RsPatTupleStruct) return null
        if (!PsiModificationUtil.canReplace(pat)) return null
        return Context(pat)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val structPat = ctx.structPat
        if (structPat is RsPatStruct) {
            expandStructFields(factory, structPat)
        } else if (structPat is RsPatTupleStruct) {
            expandTupleStructFields(factory, editor, structPat)
        }
    }
}
