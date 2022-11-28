/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoSettingsFilesService
import org.rust.ide.inspections.fixes.AttachFileToModuleFix
import org.rust.ide.notifications.confirmLoadingUntrustedProject
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.saveAllDocuments

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
                    "File is not included in module tree, analysis is not available",
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
        override fun getFamilyName(): String = "Do not show again"
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

    private class ReloadProjectQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Reload project"
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            // It seems it's impossible to be here if project is not trusted but let's be 100% sure
            if (!project.confirmLoadingUntrustedProject()) return
            saveAllDocuments()
            project.cargoProjects.refreshAllProjects()
        }
    }
}
