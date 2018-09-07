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
 * The order of properties matters in this class. When conflating an icon from simple elements, make sure that all
 * those elements are declared above to the icon.
 */
object RsIcons {
    // Logos

    val RUST: Icon = IconLoader.getIcon("/icons/rust.svg")

    // File types

    val RUST_FILE: Icon = IconLoader.getIcon("/icons/rust-file.svg")
    val MAIN_RS: Icon = IconLoader.getIcon("/icons/main-rs.svg")
    val MOD_RS: Icon = IconLoader.getIcon("/icons/mod-rs.svg")

    // Marks

    val FINAL_MARK: Icon = AllIcons.Nodes.FinalMark!!
    val STATIC_MARK: Icon = AllIcons.Nodes.StaticMark!!
    val TEST_MARK: Icon = AllIcons.Nodes.JunitTestMark!!
    val DOCS_MARK: Icon = IconLoader.getIcon("/icons/docsrs.svg")

    // Source code elements

    val CRATE: Icon = AllIcons.Nodes.PpLib!!
    val MODULE: Icon = AllIcons.Nodes.Package!!

    val TRAIT: Icon = AllIcons.Nodes.Interface!!
    val STRUCT: Icon = AllIcons.Nodes.Class!!
    val TYPE: Icon = IconLoader.getIcon("/icons/nodes/typeAlias.svg")
    val IMPL: Icon = AllIcons.Nodes.AbstractClass!!
    val ENUM: Icon = AllIcons.Nodes.Enum!!

    val METHOD: Icon = AllIcons.Nodes.Method!!
    val FUNCTION: Icon = IconLoader.getIcon("/icons/nodes/function.svg")
    val ASSOC_FUNCTION: Icon = FUNCTION.addStaticMark()
    val MACRO: Icon = AllIcons.General.ExclMark!!

    val ABSTRACT_METHOD: Icon = AllIcons.Nodes.AbstractMethod!!
    val ABSTRACT_FUNCTION: Icon = IconLoader.getIcon("/icons/nodes/abstractFunction.svg")
    val ABSTRACT_ASSOC_FUNCTION: Icon = ABSTRACT_FUNCTION.addStaticMark()

    val ATTRIBUTE: Icon = AllIcons.Nodes.Annotationtype!!
    val MUT_ARGUMENT: Icon = AllIcons.Nodes.Parameter!!
    val ARGUMENT: Icon = MUT_ARGUMENT.addFinalMark()
    val FIELD: Icon = AllIcons.Nodes.Field!!
    val MUT_BINDING: Icon = AllIcons.Nodes.Variable!!
    val BINDING: Icon = MUT_BINDING.addFinalMark()
    val GLOBAL_BINDING: Icon = IconLoader.getIcon("/icons/nodes/globalBinding.svg")
    val CONSTANT: Icon = GLOBAL_BINDING.addFinalMark()
    val MUT_STATIC: Icon = GLOBAL_BINDING.addStaticMark()
    val STATIC: Icon = MUT_STATIC.addFinalMark()
    val ENUM_VARIANT: Icon = FIELD.addFinalMark().addStaticMark()

    // Gutter

    val IMPLEMENTED: Icon = AllIcons.Gutter.ImplementedMethod!!
    val IMPLEMENTING_METHOD: Icon = AllIcons.Gutter.ImplementingMethod!!
    val OVERRIDING_METHOD: Icon = AllIcons.Gutter.OverridingMethod!!
    val RECURSIVE_CALL: Icon = AllIcons.Gutter.RecursiveMethod!!
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
