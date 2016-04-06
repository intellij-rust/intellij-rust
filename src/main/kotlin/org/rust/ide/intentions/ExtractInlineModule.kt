package org.rust.ide.intentions

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.`super`
import org.rust.lang.core.psi.util.isTopLevelInFile
import org.rust.lang.core.psi.util.ownsDirectory
import org.rust.lang.core.psi.util.parentOfType

class ExtractInlineModule : IntentionAction {
    override fun getFamilyName() = "Extract inline module structure"
    override fun getText() = "Extract inline module"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val offset = editor?.caretModel?.offset ?: return
        val mod = file.findElementAt(offset)?.parentOfType<RustModItem>() ?: return
        val modName = mod.name ?: return
        var decl = RustElementFactory.createModDeclItem(project, modName) ?: return
        decl = file.rustMod?.addBefore(decl, mod) as? RustModDeclItem ?: return
        val modFile = decl.getOrCreateModuleFile() ?: return

        val startElement = mod.lbrace.nextSibling ?: return
        val endElement = mod.rbrace?.prevSibling ?: return

        modFile.addRange(startElement, endElement)
        ReformatCodeProcessor(project, modFile, null, false).run()

        mod.delete()
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        file ?: return false
        val offset = editor?.caretModel?.offset ?: return false
        var mod = file.findElementAt(offset)?.parentOfType<RustModItem>() ?: return false
        return !mod.isTopLevelInFile && mod.`super`?.ownsDirectory ?: false
    }
}
