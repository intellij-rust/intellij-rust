package org.rust.ide.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.util.cargoProject
import org.rust.ide.actions.RustExpandModuleAction
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.impl.mixin.isPathAttributeRequired
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.psi.util.module

class RustItemsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RustElementVisitor() {
            override fun visitModDeclItem(o: RustModDeclItemElement) = checkModDecl(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RustModDeclItemElement) {
        if (modDecl.isPathAttributeRequired && modDecl.pathAttribute == null) {
            val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
            holder.createErrorAnnotation(modDecl, message)
            return
        }

        val containingMod = modDecl.containingMod ?: return
        if (!containingMod.ownsDirectory) {
            // We don't want to show the warning if there is no cargo project
            // associated with the current module. Without it we can't know for
            // sure that a mod is not a directory owner.
            if (modDecl.module?.cargoProject != null) {
                holder.createErrorAnnotation(modDecl, "Cannot declare a new module at this location")
                    .registerFix(AddModuleFile(modDecl, expandModuleFirst = true))
            }
            return
        }

        if (modDecl.reference?.resolve() == null) {
            holder.createErrorAnnotation(modDecl, "Unresolved module")
                .registerFix(AddModuleFile(modDecl, expandModuleFirst = false))
        }
    }
}

private class AddModuleFile(
    modDecl: RustModDeclItemElement,
    private val expandModuleFirst: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(modDecl) {
    override fun getText(): String = "Create module file"

    override fun getFamilyName(): String = text


    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val modDecl = startElement as RustModDeclItemElement
        if (expandModuleFirst) {
            val containingFile = modDecl.containingFile as RustFile
            RustExpandModuleAction.expandModule(containingFile)
        }
        modDecl.getOrCreateModuleFile()?.let { it.navigate(true) }
    }

}
