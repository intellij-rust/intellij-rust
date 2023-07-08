/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.util.PathUtil
import org.rust.RsBundle
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.tools.cargo
import org.rust.ide.icons.RsIcons
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.unwrapOrThrow
import javax.swing.Icon

// We implement `CustomStepProjectGenerator` as well to correctly show settings UI
// because otherwise PyCharm doesn't add peer's component into project settings panel
class RsDirectoryProjectGenerator : DirectoryProjectGeneratorBase<ConfigurationData>(),
                                    CustomStepProjectGenerator<ConfigurationData> {

    private var peer: RsProjectGeneratorPeer? = null

    override fun getName(): String = RsBundle.message("rust")
    override fun getLogo(): Icon = RsIcons.RUST
    override fun createPeer(): ProjectGeneratorPeer<ConfigurationData> = RsProjectGeneratorPeer().also { peer = it }

    override fun validate(baseDirPath: String): ValidationResult {
        val crateName = PathUtil.getFileName(baseDirPath)
        val message = peer?.settings?.template?.validateProjectName(crateName) ?: return ValidationResult.OK
        return ValidationResult(message)
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, data: ConfigurationData, module: Module) {
        val (settings, template) = data
        val cargo = settings.toolchain?.cargo() ?: return

        val name = project.name.replace(' ', '_')
        val generatedFiles = project.computeWithCancelableProgress(RsBundle.message("progress.title.generating.cargo.project")) {
            cargo.makeProject(project, module, baseDir, name, template).unwrapOrThrow() // TODO throw? really??
        }

        project.rustSettings.modify {
            it.toolchain = settings.toolchain
            it.explicitPathToStdlib = settings.explicitPathToStdlib
        }

        project.makeDefaultRunConfiguration(template)
        project.openFiles(generatedFiles)
    }

    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<ConfigurationData>,
        callback: AbstractNewProjectStep.AbstractCallback<ConfigurationData>
    ): AbstractActionWithPanel = RsProjectSettingsStep(projectGenerator)
}
