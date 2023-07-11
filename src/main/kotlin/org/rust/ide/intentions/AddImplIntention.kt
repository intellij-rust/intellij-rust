/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.openapiext.moveCaretToOffset

class AddImplIntention : RsElementBaseIntentionAction<AddImplIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.add.impl.block")
    override fun getFamilyName() = text

    class Context(
        val type: RsStructOrEnumItemElement,
        val typeName: String,
        val placeForImpl: PsiInsertionPlace,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val struct = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val typeName = struct.name ?: return null
        val placeForImpl = PsiInsertionPlace.forItemInTheScopeOf(struct) ?: return null
        return Context(struct, typeName, placeForImpl)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newImpl = RsPsiFactory(project).createInherentImplItem(ctx.typeName, ctx.type.typeParameterList, ctx.type.whereClause)
        val insertedImpl = ctx.placeForImpl.insert(newImpl)
        editor.moveCaretToOffset(insertedImpl, insertedImpl.endOffset - 1)
    }
}
