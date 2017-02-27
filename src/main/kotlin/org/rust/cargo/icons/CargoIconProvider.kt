package org.rust.cargo.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.CargoConstants
import org.rust.cargo.util.cargoProjectRoot
import org.rust.lang.core.psi.ext.module
import javax.swing.Icon

class CargoIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? = when (element) {
        is PsiFile -> getFileIcon(element)
        else -> null
    }

    private fun getFileIcon(element: PsiFile): Icon? = when {
        element.name == CargoConstants.MANIFEST_FILE -> CargoIcons.ICON
        element.name == CargoConstants.LOCK_FILE -> CargoIcons.LOCK_ICON
        isBuildRs(element) -> CargoIcons.BUILD_RS_ICON
        else -> null
    }

    private fun isBuildRs(element: PsiFile): Boolean =
        element.name == CargoConstants.BUILD_RS_FILE
            && element.containingDirectory.virtualFile == element.module?.cargoProjectRoot
}
