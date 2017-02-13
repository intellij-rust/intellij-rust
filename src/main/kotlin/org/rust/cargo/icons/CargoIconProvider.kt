package org.rust.cargo.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.CargoConstants
import javax.swing.Icon

class CargoIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? = when (element) {
        is PsiFile -> getFileIcon(element)
        else -> null
    }

    fun getFileIcon(element: PsiFile): Icon? = when (element.name) {
        CargoConstants.MANIFEST_FILE -> CargoIcons.ICON
        CargoConstants.LOCK_FILE -> CargoIcons.LOCK_ICON
        else -> null
    }
}
