/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.notifications.confirmLoadingUntrustedProject
import org.rust.openapiext.saveAllDocuments

class ReloadProjectQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.reload.project")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // It seems it's impossible to be here if project is not trusted but let's be 100% sure
        if (!project.confirmLoadingUntrustedProject()) return
        saveAllDocuments()
        project.cargoProjects.refreshAllProjects()
    }
}
