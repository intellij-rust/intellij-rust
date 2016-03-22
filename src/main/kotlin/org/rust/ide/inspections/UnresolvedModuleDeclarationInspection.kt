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
            override fun visitModDeclItem(mod: RustModDeclItem) {
                if (mod.reference?.resolve() == null) {
                    val message: String
                    val quickFix: LocalQuickFix?
                    if (mod.isPathAttributeRequired && mod.explicitPath == null ) {
                        message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
                        quickFix = null
                    } else {
                        message = "Unresolved module"
                        quickFix = if (mod.containingMod?.ownsDirectory ?: false ) AddModuleFile else null
                    }
                    holder.registerProblem(mod, message, quickFix)
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
