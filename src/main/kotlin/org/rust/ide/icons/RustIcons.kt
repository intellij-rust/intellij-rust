package org.rust.ide.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import com.intellij.util.VisibilityIcons
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

public object RustIcons {

    public val RUST     = IconLoader.getIcon("/org/rust/ide/icons/rust.png")
    public val RUST_BIG = IconLoader.getIcon("/org/rust/ide/icons/rust@2x.png")

    public val MODULE   = AllIcons.Nodes.Package

    public val TRAIT    = AllIcons.Nodes.Interface
    public val CLASS    = AllIcons.Nodes.Class
    public val IMPL     = AllIcons.Nodes.AbstractClass
    public val ENUM     = AllIcons.Nodes.Enum

    public val FIELD    = AllIcons.Nodes.Field
    public val FUNCTION = AllIcons.Nodes.Function
    public val METHOD   = AllIcons.Nodes.Method

    public val ABSTRACT_METHOD = AllIcons.Nodes.AbstractMethod

    public val STATIC_MARK  = AllIcons.Nodes.StaticMark
    public val TEST_MARK    = AllIcons.Nodes.JunitTestMark
}

fun Icon.addStaticMark(): Icon {
    return LayeredIcon(this, RustIcons.STATIC_MARK);
}

fun Icon.addTestMark(): Icon {
    return LayeredIcon(this, RustIcons.TEST_MARK);
}

fun Icon.addVisibilityIcon(pub: Boolean): RowIcon {
    val visibility = if (pub) PsiUtil.ACCESS_LEVEL_PUBLIC else PsiUtil.ACCESS_LEVEL_PRIVATE
    val icon = RowIcon(this, EmptyIcon.create(PlatformIcons.PUBLIC_ICON))
    VisibilityIcons.setVisibilityIcon(visibility, icon);
    return icon;
}
