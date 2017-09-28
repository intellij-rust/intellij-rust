/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.icons

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants
import javax.swing.Icon

class CargoIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? = when (file.name) {
        CargoConstants.MANIFEST_FILE -> CargoIcons.ICON
        CargoConstants.XARGO_MANIFEST_FILE -> CargoIcons.ICON
        CargoConstants.LOCK_FILE -> CargoIcons.LOCK_ICON
        else -> null
    }
}
