/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import org.rust.cargo.CargoConstants
import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

abstract class CargoProjectOpenProcessorBase : ProjectOpenProcessor() {
    override fun getIcon(): Icon = CargoIcons.ICON
    override fun getName(): String = "Cargo"

    override fun canOpenProject(file: VirtualFile): Boolean {
        return FileUtil.namesEqual(file.name, CargoConstants.MANIFEST_FILE) ||
            file.isDirectory && file.findChild(CargoConstants.MANIFEST_FILE) != null
    }
}
