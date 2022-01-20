/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.PlatformProjectOpenProcessor
import org.rust.cargo.project.model.guessAndSetupRustProject

// BACKCOMPAT: 2021.3. Inline it
class CargoProjectOpenProcessor : CargoProjectOpenProcessorBase() {

    override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceNewFrame: Boolean): Project? {
        val basedir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent

        return PlatformProjectOpenProcessor.getInstance().doOpenProject(basedir, projectToClose, forceNewFrame)?.also {
            StartupManager.getInstance(it).runWhenProjectIsInitialized { guessAndSetupRustProject(it) }
        }
    }
}
