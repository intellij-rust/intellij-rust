/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.getOrCreateModuleFile
import org.rust.openapiext.Testmark

//TODO: make context more precise here
class ExtractInlineModuleIntention : RsElementBaseIntentionAction<RsModItem>() {
    override fun getFamilyName() = "Extract inline module structure"
    override fun getText() = "Extract inline module"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsModItem? {
        val mod = element.ancestorOrSelf<RsModItem>() ?: return null
        if (mod.`super`?.ownsDirectory != true) return null
        return mod
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsModItem) {
        val modName = ctx.name ?: return
        var decl = RsPsiFactory(project).createModDeclItem(modName)
        decl = ctx.parent?.addBefore(decl, ctx) as? RsModDeclItem ?: return

        if (ctx.firstChild != ctx.mod) {
            Testmarks.copyAttrs.hit()
            decl.addRangeBefore(ctx.firstChild, ctx.mod.prevSibling, decl.mod)
        }

        val modFile = decl.getOrCreateModuleFile() ?: return

        val startElement = ctx.lbrace.nextSibling ?: return
        val endElement = ctx.rbrace?.prevSibling ?: return

        modFile.addRange(startElement, endElement)

        ctx.delete()
    }

    object Testmarks {
        val copyAttrs = Testmark("copyAttrs")
    }
}
