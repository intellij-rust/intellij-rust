/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

class RsDirectoryProjectGenerator : DirectoryProjectGeneratorBase<ConfigurationData>() {

    override fun getName(): String = "Rust"
    override fun getLogo(): Icon? = RsIcons.RUST
    override fun createPeer(): ProjectGeneratorPeer<ConfigurationData> = RsProjectGeneratorPeer()

    override fun generateProject(project: Project, baseDir: VirtualFile, data: ConfigurationData, module: Module) {
        val (settings, createBinary) = data
        settings.toolchain?.rawCargo()?.init(module, baseDir, createBinary)
    }
}
