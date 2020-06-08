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
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.inspections.fixes.AttachFileToModuleFix
import org.rust.lang.core.psi.RsFile

class RsDetachedFileInspection : RsLocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val rsFile = file as? RsFile ?: return null
        val project = file.project

        if (!isInspectionEnabled(project, file.virtualFile)) return null

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.initialized) return null

        // Handled by [NoCargoProjectNotificationProvider]
        if (cargoProjects.findProjectForFile(file.virtualFile) == null) return null

        if (rsFile.crateRoot == null) {
            val availableModules = AttachFileToModuleFix.findAvailableModulesForFile(project, rsFile)
            val attachFix = if (availableModules.isNotEmpty()) {
                val moduleLabel = if (availableModules.size == 1) {
                    availableModules[0].name
                } else {
                    null
                }
                AttachFileToModuleFix(rsFile, moduleLabel)
            } else {
                null
            }

            val fixes = listOfNotNull(
                attachFix,
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
}
