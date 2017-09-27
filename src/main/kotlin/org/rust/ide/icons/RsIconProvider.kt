/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.icons.CargoIcons
import org.rust.ide.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoPackage
import javax.swing.Icon

class RsIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? = when (element) {
        is RsFile -> getFileIcon(element)
        else -> null
    }

    private fun getFileIcon(file: RsFile): Icon? = when {
        file.name == RsConstants.MOD_RS_FILE -> RsIcons.MOD_RS
        isMainFile(file) -> RsIcons.MAIN_RS
        isBuildRs(file) -> CargoIcons.BUILD_RS_ICON
        else -> null
    }

    private fun isMainFile(element: RsFile) =
        (element.name == RsConstants.MAIN_RS_FILE || element.name == RsConstants.LIB_RS_FILE)
            && element.isCrateRoot

    private fun isBuildRs(element: RsFile): Boolean =
        element.name == CargoConstants.BUILD_RS_FILE
            && element.containingCargoPackage?.contentRoot == element.containingDirectory?.virtualFile
}
