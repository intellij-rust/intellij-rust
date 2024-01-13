/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import org.rust.bsp.BspConstants
import org.rust.cargo.CargoConstants
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.guessAndSetupRustProject
import javax.swing.Icon

class CargoProjectOpenProcessor : ProjectOpenProcessor() {

    override val icon: Icon get() = CargoIcons.ICON
    override val name: String get() = "Cargo"

    override fun canOpenProject(file: VirtualFile): Boolean {
        return FileUtil.namesEqual(file.name, CargoConstants.MANIFEST_FILE) ||
            file.isDirectory && file.findChild(CargoConstants.MANIFEST_FILE) != null ||
            FileUtil.namesEqual(file.name, BspConstants.BSP_WORKSPACE) ||
            file.isDirectory && file.findChild(BspConstants.BSP_WORKSPACE) != null
    }

    override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceNewFrame: Boolean): Project? {
        val basedir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent

        return PlatformProjectOpenProcessor.getInstance().doOpenProject(basedir, projectToClose, forceNewFrame)?.also {
            StartupManager.getInstance(it).runWhenProjectIsInitialized { guessAndSetupRustProject(it) }
        }
    }
}
