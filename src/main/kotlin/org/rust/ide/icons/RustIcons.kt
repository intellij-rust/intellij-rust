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

object RustIcons {
    val RUST     = IconLoader.getIcon("/org/rust/ide/icons/rust.png")
    val RUST_BIG = IconLoader.getIcon("/org/rust/ide/icons/rust-big.png")

    val RUST_FILE = IconLoader.getIcon("/org/rust/ide/icons/rust-file.png")

    val MODULE   = AllIcons.Nodes.Package

    val TRAIT    = AllIcons.Nodes.Interface
    val STRUCT   = AllIcons.Nodes.Class
    val IMPL     = AllIcons.Nodes.AbstractClass
    val ENUM     = AllIcons.Nodes.Enum

    val FIELD    = AllIcons.Nodes.Field
    val FUNCTION = AllIcons.Nodes.Function
    val METHOD   = AllIcons.Nodes.Method

    val ABSTRACT_METHOD = AllIcons.Nodes.AbstractMethod

    val STATIC_MARK  = AllIcons.Nodes.StaticMark
    val TEST_MARK    = AllIcons.Nodes.JunitTestMark
}

fun Icon.addStaticMark(): Icon = LayeredIcon(this, RustIcons.STATIC_MARK)

fun Icon.addTestMark(): Icon = LayeredIcon(this, RustIcons.TEST_MARK)

fun Icon.addVisibilityIcon(pub: Boolean): RowIcon {
    val visibility = if (pub) PsiUtil.ACCESS_LEVEL_PUBLIC else PsiUtil.ACCESS_LEVEL_PRIVATE
    val icon = RowIcon(this, EmptyIcon.create(PlatformIcons.PUBLIC_ICON))
    VisibilityIcons.setVisibilityIcon(visibility, icon)
    return icon
}
