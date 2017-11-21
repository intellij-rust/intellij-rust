/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.ancestorStrict

class AddImplIntention : RsElementBaseIntentionAction<AddImplIntention.Context>() {
    override fun getText() = "Add impl block"
    override fun getFamilyName() = text

    class Context(val type: RsStructOrEnumItemElement, val name: String)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val struct = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val name = struct.name ?: return null
        return Context(struct, name)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        var impl = RsPsiFactory(project).createInherentImplItem(ctx.name, ctx.type.typeParameterList, ctx.type.whereClause)

        impl = ctx.type.parent.addAfter(impl, ctx.type) as RsImplItem

        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }
}
