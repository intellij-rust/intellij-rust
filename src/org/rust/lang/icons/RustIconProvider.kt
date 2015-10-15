package org.rust.lang.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.impl.RustFnItemImpl
import org.rust.lang.core.psi.impl.RustImplMethodImpl
import javax.swing.Icon

class RustIconProvider: IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when (element) {
            is RustFnItemImpl -> RustIcons.FUNCTION
            is RustImplMethodImpl -> RustIcons.METHOD
            else -> null
        }
    }
}