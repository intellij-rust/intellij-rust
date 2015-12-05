package org.rust.cargo.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.Cargo
import javax.swing.Icon


public class CargoIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when (element) {
            is PsiFile -> getFileIcon(element)
            else       -> null
        }
    }

    fun getFileIcon(element: PsiFile): Icon? {
        return when (element.name) {
            Cargo.BUILD_FILE -> CargoIcons.ICON
            Cargo.LOCK_FILE  -> CargoIcons.LOCK_ICON
            else             -> null
        }
    }
}
