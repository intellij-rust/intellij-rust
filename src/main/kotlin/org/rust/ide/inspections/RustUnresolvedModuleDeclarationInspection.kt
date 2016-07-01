package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustMod
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
                            MoveModuleToDedicatedDirectoryQuickFix())

                    }
                    return
                }

                if (modDecl.reference?.resolve() == null) {
                    holder.registerProblem(modDecl, "Unresolved module", AddModuleFile)
                }
            }
        }

    object AddModuleFile : LocalQuickFixBase("Create module file") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val mod = descriptor.psiElement as RustModDeclItemElement
            val file = mod.getOrCreateModuleFile() ?: return
            file.navigate(true)
        }

    }

    class MoveModuleToDedicatedDirectoryQuickFix : LocalQuickFixBase("Move parent module to a dedicated directory") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement.containingFile as RustFile
            val dirName = FileUtil.getNameWithoutExtension(file.name)
            val directory = file.parent?.createSubdirectory(dirName)
            MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
            PsiFileImplUtil.setName(file, RustMod.MOD_RS)
        }

    }
}
