package org.rust.lang.icons

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import com.intellij.util.VisibilityIcons
import com.intellij.util.ui.EmptyIcon
import org.rust.lang.core.psi.*
import javax.swing.Icon

class RustIconProvider: IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when (element) {
            is RustTraitItem -> getTraitIcon(element, flags)
            is RustTraitMethod -> getTraitMethodIcon(element, flags)
            is RustImplItem -> RustIcons.IMPL
            is RustEnumItem -> getEnumIcon(element, flags)
            is RustEnumDef -> RustIcons.FIELD
            is RustStructItem -> getStructIcon(element, flags)
            is RustStructDeclField -> getStructDeclFieldIcon(element, flags)
            is RustFnItem -> RustIcons.FUNCTION
            is RustImplMethod -> getImplMethodIcon(element, flags)
            else -> null
        }
    }

    private fun getTraitIcon(element: RustTraitItem, flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.TRAIT;

        return RustIcons.TRAIT.addVisibilityIcon(element.isPublic())
    }

    private fun getTraitMethodIcon(element: RustTraitMethod, flags: Int): Icon? {
        var icon = if (element.isAbstract()) RustIcons.ABSTRACT_METHOD else RustIcons.METHOD
        if (element.isStatic())
            icon = icon.addStaticMark()

        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(element.isPublic())
    }

    private fun getImplMethodIcon(element: RustImplMethod, flags: Int): Icon? {
        val icon = if (element.isStatic()) RustIcons.METHOD.addStaticMark() else RustIcons.METHOD
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(element.isPublic())
    }

    private fun getEnumIcon(element: RustEnumItem, flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.ENUM;

        return RustIcons.ENUM.addVisibilityIcon(element.isPublic())
    }

    private fun getStructIcon(element: RustStructItem, flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.CLASS;

        return RustIcons.CLASS.addVisibilityIcon(element.isPublic())
    }

    private fun getStructDeclFieldIcon(element: RustStructDeclField, flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.FIELD;

        return RustIcons.FIELD.addVisibilityIcon(element.isPublic())
    }
}

fun RustItem.isPublic(): Boolean {
    return vis != null;
}

fun RustStructDeclField.isPublic(): Boolean {
    return vis != null;
}

fun RustImplMethod.isPublic(): Boolean {
    return vis != null;
}

fun RustImplMethod.isStatic(): Boolean {
    return self == null;
}

fun RustTraitMethod.isPublic(): Boolean {
    return vis != null;
}

fun RustTraitMethod.isAbstract(): Boolean {
    return this is RustTypeMethod;
}

fun RustTraitMethod.isStatic(): Boolean {
    return self == null;
}

fun Icon.addVisibilityIcon(pub: Boolean): RowIcon {
    val visibility = if (pub) PsiUtil.ACCESS_LEVEL_PUBLIC else PsiUtil.ACCESS_LEVEL_PRIVATE
    val icon = RowIcon(this, EmptyIcon.create(PlatformIcons.PUBLIC_ICON))
    VisibilityIcons.setVisibilityIcon(visibility, icon);
    return icon;
}

fun Icon.addStaticMark(): Icon {
    return LayeredIcon(this, RustIcons.STATIC_MARK);
}
