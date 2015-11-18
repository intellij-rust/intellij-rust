package org.rust.lang.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.util.ChildModFile
import org.rust.lang.core.psi.util.moduleFile

class NonexistentModuleDeclarationInspection : LocalInspectionTool() {
    val NONEXISTENT_MODULE_QUICKFIX = object: LocalQuickFixBase("Create module file") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val modFileName = "${descriptor.psiElement.text}.rs"
            val modFile = descriptor.psiElement.containingFile.containingDirectory.createFile(modFileName)
            FileEditorManager.getInstance(project).openFile(modFile.virtualFile, true)
        }
    }

    override fun getGroupDisplayName() = "Rust"

    override fun getDisplayName() = "Nonexistent module declaration"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object: RustVisitor() {
            override fun visitModDeclItem(o: RustModDeclItem) {
                if (o.moduleFile is ChildModFile.NotFound) {
                    holder.registerProblem(o.identifier, "Nonexistent module declaration found. " +
                        "Apply quick fix to create a module file.", NONEXISTENT_MODULE_QUICKFIX)
                }
            }
        }
    }
}
