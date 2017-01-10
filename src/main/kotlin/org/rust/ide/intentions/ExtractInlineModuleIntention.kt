package org.rust.ide.intentions

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.util.parentOfType

//TODO: make context more precise here
class ExtractInlineModuleIntention : RustElementBaseIntentionAction<RustModItemElement>() {
    override fun getFamilyName() = "Extract inline module structure"
    override fun getText() = "Extract inline module"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustModItemElement? {
        val mod = element.parentOfType<RustModItemElement>() ?: return null
        if (mod.`super`?.ownsDirectory != true) return null
        return mod

    }

    override fun invoke(project: Project, editor: Editor, ctx: RustModItemElement) {
        val modName = ctx.name ?: return
        var decl = RustPsiFactory(project).createModDeclItem(modName)
        decl = ctx.parent?.addBefore(decl, ctx) as? RustModDeclItemElement ?: return
        val modFile = decl.getOrCreateModuleFile() ?: return

        val startElement = ctx.lbrace.nextSibling ?: return
        val endElement = ctx.rbrace?.prevSibling ?: return

        modFile.addRange(startElement, endElement)
        ReformatCodeProcessor(project, modFile, null, false).run()

        ctx.delete()
    }
}
