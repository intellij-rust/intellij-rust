/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import org.rust.ide.icons.RsIcons
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
        val isBinary = peer?.settings?.createBinary == true
        val message = RsPackageNameValidator.validate(crateName, isBinary) ?: return ValidationResult.OK
        return ValidationResult(message)
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, data: ConfigurationData, module: Module) {
        val (settings, createBinary) = data
        val generatedFiles = settings.toolchain?.rawCargo()?.init(module, baseDir, createBinary) ?: return

        // Open new files
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
            val navigation = PsiNavigationSupport.getInstance()
            navigation.createNavigatable(project, generatedFiles.manifest, -1).navigate(false)
            for (file in generatedFiles.sourceFiles) {
                navigation.createNavigatable(project, file, -1).navigate(true)
            }
        }
    }

    override fun createStep(projectGenerator: DirectoryProjectGenerator<ConfigurationData>,
                            callback: AbstractNewProjectStep.AbstractCallback<ConfigurationData>): AbstractActionWithPanel =
        RsProjectSettingsStep(projectGenerator)
}
