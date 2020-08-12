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
import org.rust.ide.icons.RsIcons
import org.rust.openapiext.computeWithCancelableProgress
import java.io.File
import javax.swing.Icon

// We implement `CustomStepProjectGenerator` as well to correctly show settings UI
// because otherwise PyCharm doesn't add peer's component into project settings panel
class RsDirectoryProjectGenerator : DirectoryProjectGeneratorBase<ConfigurationData>(),
                                    CustomStepProjectGenerator<ConfigurationData> {

    private var peer: RsProjectGeneratorPeer? = null

    override fun getName(): String = "Rust"
    override fun getLogo(): Icon? = RsIcons.RUST
    override fun createPeer(): ProjectGeneratorPeer<ConfigurationData> = RsProjectGeneratorPeer().also { peer = it }

    override fun validate(baseDirPath: String): ValidationResult {
        val crateName = File(baseDirPath).nameWithoutExtension
        val message = peer?.settings?.template?.validateProjectName(crateName) ?: return ValidationResult.OK
        return ValidationResult(message)
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, data: ConfigurationData, module: Module) {
        val (settings, template) = data
        val cargo = settings.toolchain?.rawCargo() ?: return

        val name = project.name.replace(' ', '_')
        val generatedFiles = project.computeWithCancelableProgress("Generating Cargo project...") {
            cargo.makeProject(project, module, baseDir, name, template)
        } ?: return

        project.makeDefaultRunConfiguration(template)
        project.openFiles(generatedFiles)
    }

    override fun createStep(projectGenerator: DirectoryProjectGenerator<ConfigurationData>,
                            callback: AbstractNewProjectStep.AbstractCallback<ConfigurationData>): AbstractActionWithPanel =
        RsProjectSettingsStep(projectGenerator)
}
