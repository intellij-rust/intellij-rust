package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
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

class RustUnresolvedModuleDeclarationInspection : RustLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustElementVisitor() {
            override fun visitModDeclItem(modDecl: RustModDeclItemElement) {
                if (modDecl.isPathAttributeRequired && modDecl.pathAttribute == null) {
                    val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
                    holder.registerProblem(modDecl, message)
                    return
                }

                val containingMod = modDecl.containingMod ?: return
                if (!containingMod.ownsDirectory) {
                    // We don't want to show the warning if there is no cargo project
                    // associated with the current module. Without it we can't know for
                    // sure that a mod is not a directory owner.
                    if (modDecl.module?.cargoProject != null) {
                        holder.registerProblem(modDecl, "Cannot declare a new module at this location",
                            AddModuleFile(expandModuleFirst = true))

                    }
                    return
                }

                if (modDecl.reference?.resolve() == null) {
                    holder.registerProblem(modDecl, "Unresolved module", AddModuleFile(expandModuleFirst = false))
                }
            }
        }

    private class AddModuleFile(private val expandModuleFirst: Boolean): LocalQuickFixBase("Create module file") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val mod = descriptor.psiElement as RustModDeclItemElement
            if (expandModuleFirst) {
                val containingFile = mod.containingFile as RustFile
                RustExpandModuleAction.expandModule(containingFile)
            }
            val file = mod.getOrCreateModuleFile() ?: return
            file.navigate(true)
        }

    }

}
