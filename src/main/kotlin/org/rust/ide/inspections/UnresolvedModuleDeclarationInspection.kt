package org.rust.ide.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.util.ownsDirectory

class UnresolvedModuleDeclarationInspection : RustLocalInspectionTool() {

    override fun getDisplayName(): String = "Unresolved module declaration"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustVisitor() {
            override fun visitModDeclItem(mod: RustModDeclItem) {
                if (mod.reference?.resolve() == null) {
                    if (mod.containingMod?.ownsDirectory ?: false) {
                        holder.registerProblem(mod, "Unresolved module", AddModuleFile)
                    } else {
                        holder.registerProblem(mod, "Unresolved module")
                    }
                }
            }
        }

    object AddModuleFile : LocalQuickFixBase("Create module file") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val mod = descriptor.psiElement as RustModDeclItem
            val file = mod.getOrCreateModuleFile() ?: return
            FileEditorManager.getInstance(project).openFile(file.virtualFile, /* focusEditor = */ true)
        }

    }
}
