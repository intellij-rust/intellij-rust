/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.impl.confirmOpeningUntrustedProject
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.ide.security.isNewTrustedProjectApiAvailable
import javax.swing.Icon

class CargoProjectOpenProcessor : ProjectOpenProcessor() {
    override fun getIcon(): Icon = CargoIcons.ICON
    override fun getName(): String = "Cargo"

    override fun canOpenProject(file: VirtualFile): Boolean {
        return FileUtil.namesEqual(file.name, CargoConstants.MANIFEST_FILE) ||
            file.isDirectory && file.findChild(CargoConstants.MANIFEST_FILE) != null
    }

    @Suppress("UnstableApiUsage")
    override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceNewFrame: Boolean): Project? {
        val basedir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent

        var choice: OpenUntrustedProjectChoice? = null
        // Starting with 2021.3.1 and 2021.2.4 IDE always ask users
        // if they trust project before opening, i.e. we don't need to check it manually.
        // Moreover, the corresponding API was changed, so we should avoid using old API
        // not to produce runtime errors
        if (!isNewTrustedProjectApiAvailable) {
            choice = confirmOpeningUntrustedProject(basedir, listOf(RsBundle.message("cargo")))
            if (choice == OpenUntrustedProjectChoice.CANCEL) return null
        }

        return PlatformProjectOpenProcessor.getInstance().doOpenProject(basedir, projectToClose, forceNewFrame)?.also {
            if (!isNewTrustedProjectApiAvailable) {
                it.setTrusted(choice == OpenUntrustedProjectChoice.IMPORT)
            }
            StartupManager.getInstance(it).runWhenProjectIsInitialized { guessAndSetupRustProject(it) }
        }
    }
}
