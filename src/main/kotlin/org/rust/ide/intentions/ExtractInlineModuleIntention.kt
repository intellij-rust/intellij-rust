package org.rust.ide.intentions

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.util.parentOfType

class ExtractInlineModuleIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = "Extract inline module structure"
    override fun getText() = "Extract inline module"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val mod = element.parentOfType<RustModItemElement>() ?: return
        val modName = mod.name ?: return
        var decl = RustElementFactory.createModDeclItem(project, modName) ?: return
        decl = mod.parent?.addBefore(decl, mod) as? RustModDeclItemElement ?: return
        val modFile = decl.getOrCreateModuleFile() ?: return

        val startElement = mod.lbrace.nextSibling ?: return
        val endElement = mod.rbrace?.prevSibling ?: return

        modFile.addRange(startElement, endElement)
        ReformatCodeProcessor(project, modFile, null, false).run()

        mod.delete()
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val mod = element.parentOfType<RustModItemElement>() ?: return false
        return mod.`super`?.ownsDirectory ?: false
    }
}
