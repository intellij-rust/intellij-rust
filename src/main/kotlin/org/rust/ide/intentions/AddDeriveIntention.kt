/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.findOuterAttr
import org.rust.lang.core.psi.ext.firstKeyword
import org.rust.openapiext.moveCaretToOffset

class AddDeriveIntention : RsElementBaseIntentionAction<AddDeriveIntention.Context>() {
    override fun getFamilyName() = RsBundle.message("intention.name.add.derive.clause")
    override fun getText() = RsBundle.message("intention.name.add.derive.clause")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    class Context(
        val item: RsStructOrEnumItemElement,
        val itemStart: PsiElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val item = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val keyword = item.firstKeyword ?: return null
        return Context(item, keyword)

    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val deriveAttr = findOrCreateDeriveAttr(project, ctx.item, ctx.itemStart)
        moveCaret(editor, deriveAttr)

    }

    private fun findOrCreateDeriveAttr(project: Project, item: RsStructOrEnumItemElement, keyword: PsiElement): RsOuterAttr {
        val existingDeriveAttr = item.findOuterAttr("derive")
        if (existingDeriveAttr != null) {
            return existingDeriveAttr
        }

        val attr = RsPsiFactory(project).createOuterAttr("derive()")
        return item.addBefore(attr, keyword) as RsOuterAttr
    }

    private fun moveCaret(editor: Editor, deriveAttr: RsOuterAttr) {
        val offset = deriveAttr.metaItem.metaItemArgs?.rparen?.textOffset ?:
            deriveAttr.rbrack.textOffset
        editor.moveCaretToOffset(deriveAttr, offset)
    }
}

