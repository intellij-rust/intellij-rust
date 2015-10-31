package org.rust.lang.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.impl.*
import javax.swing.Icon

class RustIconProvider: IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when (element) {
            is RustEnumItemImpl -> RustIcons.ENUM
            is RustStructItemImpl -> RustIcons.CLASS
            is RustFnItemImpl -> RustIcons.FUNCTION
            is RustImplMethodImpl -> RustIcons.METHOD
            else -> null
        }
    }
}
