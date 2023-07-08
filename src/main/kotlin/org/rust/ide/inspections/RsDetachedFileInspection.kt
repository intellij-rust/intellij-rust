/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoSettingsFilesService
import org.rust.ide.fixes.AttachFileToModuleFix
import org.rust.ide.fixes.ReloadProjectQuickFix
import org.rust.lang.core.psi.RsFile

class RsDetachedFileInspection : RsLocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is RsFile) return null
        val project = file.project

        if (!isInspectionEnabled(project, file.virtualFile)) return null

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.initialized) return null

        // Handled by [NoCargoProjectNotificationProvider]
        val cargoProject = cargoProjects.findProjectForFile(file.virtualFile) ?: return null
        if (cargoProject.workspace == null) return null

        if (file.crateRoot == null) {
            val virtualFile = file.virtualFile
            val pkg = project.cargoProjects.findPackageForFile(virtualFile) ?: return null
            val implicitTargets = CargoSettingsFilesService.collectImplicitTargets(pkg)
            val mainFix = if (virtualFile in implicitTargets) {
                ReloadProjectQuickFix()
            } else {
                AttachFileToModuleFix.createIfCompatible(project, file)
            }

            val fixes = listOfNotNull(
                mainFix,
                SuppressFix()
            )

            return arrayOf(
                manager.createProblemDescriptor(file,
                    RsBundle.message("inspection.message.file.not.included.in.module.tree.analysis.not.available"),
                    isOnTheFly,
                    fixes.toTypedArray(),
                    ProblemHighlightType.WARNING
                )
            )
        }

        return null
    }

    override val isSyntaxOnly: Boolean = true

    private fun isInspectionEnabled(project: Project, file: VirtualFile): Boolean =
        !PropertiesComponent.getInstance(project).getBoolean(file.disablingKey, false)

    private class SuppressFix : SuppressQuickFix {
        override fun getFamilyName(): String = RsBundle.message("intention.family.name.do.not.show.again")
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.startElement as? RsFile ?: return
            PropertiesComponent.getInstance(project).setValue(file.virtualFile.disablingKey, true)
        }

        override fun isAvailable(project: Project, context: PsiElement): Boolean = true
        override fun isSuppressAll(): Boolean = false
    }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.disableDetachedFileInspection"

        private val VirtualFile.disablingKey: String
            get() = NOTIFICATION_STATUS_KEY + path
    }
}
