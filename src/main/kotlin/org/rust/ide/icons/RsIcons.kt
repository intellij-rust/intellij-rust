/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import javax.swing.Icon

/**
 * Icons that are used by various plugin components.
 *
 * The order of properties matters in this class. When conflating an icon from simple elements,
 * make sure that all those elements are declared above to the icon.
 */
object RsIcons {
    // Logos

    val RUST = IconLoader.getIcon("/icons/rust.png")

    // File types

    val RUST_FILE = IconLoader.getIcon("/icons/rust-file.png")
    val MAIN_RS = IconLoader.getIcon("/icons/main-rs.png")
    val MOD_RS = IconLoader.getIcon("/icons/mod-rs.png")

    // Marks

    val FINAL_MARK = AllIcons.Nodes.FinalMark!!
    val STATIC_MARK = AllIcons.Nodes.StaticMark!!
    val TEST_MARK = AllIcons.Nodes.JunitTestMark!!
    val DOCS_MARK = IconLoader.getIcon("/icons/docsrs.png")

    // Source code elements

    val CRATE = AllIcons.Nodes.PpLib!!
    val MODULE = AllIcons.Nodes.Package!!

    val TRAIT = AllIcons.Nodes.Interface!!
    val STRUCT = AllIcons.Nodes.Class!!
    val TYPE = IconLoader.getIcon("/icons/nodes/typeAlias.png")
    val IMPL = AllIcons.Nodes.AbstractClass!!
    val ENUM = AllIcons.Nodes.Enum!!

    val METHOD = AllIcons.Nodes.Method!!
    val FUNCTION = IconLoader.getIcon("/icons/nodes/function.png")
    val ASSOC_FUNCTION = FUNCTION.addStaticMark()
    val MACRO = AllIcons.General.ExclMark!!

    val ABSTRACT_METHOD = AllIcons.Nodes.AbstractMethod!!
    val ABSTRACT_FUNCTION = IconLoader.getIcon("/icons/nodes/abstractFunction.png")
    val ABSTRACT_ASSOC_FUNCTION = ABSTRACT_FUNCTION.addStaticMark()

    val ATTRIBUTE = AllIcons.Nodes.Annotationtype!!
    val MUT_ARGUMENT = AllIcons.Nodes.Parameter!!
    val ARGUMENT = MUT_ARGUMENT.addFinalMark()
    val FIELD = AllIcons.Nodes.Field!!
    val MUT_BINDING = AllIcons.Nodes.Variable!!
    val BINDING = MUT_BINDING.addFinalMark()
    val GLOBAL_BINDING = IconLoader.getIcon("/icons/nodes/globalBinding.png")
    val CONSTANT = GLOBAL_BINDING.addFinalMark()
    val MUT_STATIC = GLOBAL_BINDING.addStaticMark()
    val STATIC = MUT_STATIC.addFinalMark()
    val ENUM_VARIANT = FIELD.addFinalMark().addStaticMark()

    // Gutter

    val IMPLEMENTED = AllIcons.Gutter.ImplementedMethod!!
    val IMPLEMENTING_METHOD = AllIcons.Gutter.ImplementingMethod!!
    val OVERRIDING_METHOD = AllIcons.Gutter.OverridingMethod!!
    val RECURSIVE_CALL = AllIcons.Gutter.RecursiveMethod!!
}

fun Icon.addFinalMark(): Icon = LayeredIcon(this, RsIcons.FINAL_MARK)

fun Icon.addStaticMark(): Icon = LayeredIcon(this, RsIcons.STATIC_MARK)

fun Icon.addTestMark(): Icon = LayeredIcon(this, RsIcons.TEST_MARK)

fun Icon.addVisibilityIcon(pub: Boolean): RowIcon =
    RowIcon(this, if (pub) PlatformIcons.PUBLIC_ICON else PlatformIcons.PRIVATE_ICON)

fun Icon.multiple(): Icon {
    val compoundIcon = LayeredIcon(2)
    compoundIcon.setIcon(this, 0, 2 * iconWidth / 5, 0)
    compoundIcon.setIcon(this, 1, 0, 0)
    return compoundIcon
}
