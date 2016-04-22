package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.mixin.explicitPath
import org.rust.lang.core.psi.impl.mixin.getOrCreateModuleFile
import org.rust.lang.core.psi.impl.mixin.isPathAttributeRequired
import org.rust.lang.core.psi.util.ownsDirectory

class UnresolvedModuleDeclarationInspection : RustLocalInspectionTool() {

    override fun getDisplayName(): String = "Unresolved module declaration"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustVisitor() {
            override fun visitModDeclItem(modDecl: RustModDeclItem) {
                if (modDecl.isPathAttributeRequired && modDecl.explicitPath == null ) {
                    val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
                    holder.registerProblem(modDecl, message)
                    return
                }

                val containingMod = modDecl.containingMod ?: return
                if (!containingMod.ownsDirectory) {
                    holder.registerProblem(modDecl, "Cannot declare a new module at this location")
                    return
                }

                if (modDecl.reference?.resolve() == null) {
                    holder.registerProblem(modDecl, "Unresolved module", AddModuleFile)
                }
            }
        }

    object AddModuleFile : LocalQuickFixBase("Create module file") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val mod = descriptor.psiElement as RustModDeclItem
            val file = mod.getOrCreateModuleFile() ?: return
            file.navigate(true)
        }

    }
}
