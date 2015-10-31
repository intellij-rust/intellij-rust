package org.rust.lang.icons

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import com.intellij.util.VisibilityIcons
import com.intellij.util.ui.EmptyIcon
import org.rust.lang.core.psi.RustStructDeclField
import org.rust.lang.core.psi.impl.*
import javax.swing.Icon

class RustIconProvider: IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when (element) {
            is RustEnumItemImpl -> RustIcons.ENUM
            is RustStructItemImpl -> RustIcons.CLASS
            is RustStructDeclField -> getStructDeclFieldIcon(element, flags)
            is RustFnItemImpl -> RustIcons.FUNCTION
            is RustImplMethodImpl -> RustIcons.METHOD
            else -> null
        }
    }

    private fun getStructDeclFieldIcon(element: RustStructDeclField, flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.FIELD;

        val icon = RowIcon(RustIcons.FIELD, EmptyIcon.create(PlatformIcons.PUBLIC_ICON))
        val visibility = if (element.pub == null) PsiUtil.ACCESS_LEVEL_PRIVATE else PsiUtil.ACCESS_LEVEL_PUBLIC
        VisibilityIcons.setVisibilityIcon(visibility, icon);
        return icon;
    }
}
