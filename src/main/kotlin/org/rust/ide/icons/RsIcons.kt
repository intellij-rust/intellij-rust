/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.Color
import java.awt.image.RGBImageFilter
import javax.swing.Icon

/**
 * Icons that are used by various plugin components.
 *
 * The order of properties matters in this class. When conflating an icon from simple elements,
 * make sure that all those elements are declared above to the icon.
 */
object RsIcons {
    // Logos

    val RUST = IconLoader.getIcon("/icons/rust.svg")

    // File types

    val RUST_FILE = IconLoader.getIcon("/icons/rust-file.svg")
    val MAIN_RS = IconLoader.getIcon("/icons/main-rs.svg")
    val MOD_RS = IconLoader.getIcon("/icons/mod-rs.svg")

    // Marks

    val FINAL_MARK = AllIcons.Nodes.FinalMark
    val STATIC_MARK = AllIcons.Nodes.StaticMark
    val TEST_MARK = AllIcons.Nodes.JunitTestMark
    val DOCS_MARK = IconLoader.getIcon("/icons/docsrs.svg")
    val FEATURE_CHECKED_MARK = AllIcons.Diff.GutterCheckBoxSelected
    val FEATURE_UNCHECKED_MARK = AllIcons.Diff.GutterCheckBox
    val FEATURE_CHECKED_MARK_GRAYED = FEATURE_CHECKED_MARK.grayed()
    val FEATURE_UNCHECKED_MARK_GRAYED = FEATURE_UNCHECKED_MARK.grayed()
    val FEATURES_SETTINGS = AllIcons.General.Settings

    // Source code elements

    val CRATE = AllIcons.Nodes.PpLib
    val MODULE = IconLoader.getIcon("/icons/nodes/module.svg")

    val TRAIT = IconLoader.getIcon("/icons/nodes/trait.svg")
    val STRUCT = IconLoader.getIcon("/icons/nodes/struct.svg")
    val UNION = IconLoader.getIcon("/icons/nodes/union.svg")
    val ENUM = IconLoader.getIcon("/icons/nodes/enum.svg")
    val TYPE_ALIAS = IconLoader.getIcon("/icons/nodes/typeAlias.svg")
    val IMPL = IconLoader.getIcon("/icons/nodes/impl.svg")
    val FUNCTION = IconLoader.getIcon("/icons/nodes/function.svg")
    val MACRO = AllIcons.General.ExclMark

    val GLOBAL_BINDING = IconLoader.getIcon("/icons/nodes/constant.svg")
    val CONSTANT = GLOBAL_BINDING.addFinalMark()
    // TODO: look like we need separate icons for statics and constants
    val MUT_STATIC = GLOBAL_BINDING
    val STATIC = MUT_STATIC.addFinalMark()

    val METHOD = IconLoader.getIcon("/icons/nodes/method.svg")
    val ASSOC_FUNCTION = FUNCTION.addStaticMark()
    val ASSOC_CONSTANT = CONSTANT.addStaticMark()
    val ASSOC_TYPE_ALIAS = TYPE_ALIAS.addStaticMark()

    val ABSTRACT_METHOD = IconLoader.getIcon("/icons/nodes/abstractMethod.svg")
    val ABSTRACT_ASSOC_FUNCTION = IconLoader.getIcon("/icons/nodes/abstractFunction.svg").addStaticMark()
    val ABSTRACT_ASSOC_CONSTANT = IconLoader.getIcon("/icons/nodes/abstractConstant.svg").addStaticMark().addFinalMark()
    val ABSTRACT_ASSOC_TYPE_ALIAS = IconLoader.getIcon("/icons/nodes/abstractTypeAlias.svg").addStaticMark()

    val ATTRIBUTE = AllIcons.Nodes.Annotationtype
    val MUT_ARGUMENT = AllIcons.Nodes.Parameter
    val ARGUMENT = MUT_ARGUMENT.addFinalMark()
    val MUT_BINDING = AllIcons.Nodes.Variable
    val BINDING = MUT_BINDING.addFinalMark()

    val FIELD = IconLoader.getIcon("/icons/nodes/field.svg")
    val ENUM_VARIANT = IconLoader.getIcon("/icons/nodes/enumVariant.svg")

    // Gutter

    val IMPLEMENTED = AllIcons.Gutter.ImplementedMethod
    val IMPLEMENTING_METHOD = AllIcons.Gutter.ImplementingMethod
    val OVERRIDING_METHOD = AllIcons.Gutter.OverridingMethod
    val RECURSIVE_CALL = AllIcons.Gutter.RecursiveMethod

    // Repl

    val REPL = IconLoader.getIcon("/icons/repl.svg")

    val CARGO_GENERATE = IconLoader.getIcon("/icons/cargo-generate.svg")
    val WASM_PACK = IconLoader.getIcon("/icons/wasm-pack.svg")
}

fun Icon.addFinalMark(): Icon = LayeredIcon(this, RsIcons.FINAL_MARK)

fun Icon.addStaticMark(): Icon = LayeredIcon(this, RsIcons.STATIC_MARK)

fun Icon.addTestMark(): Icon = LayeredIcon(this, RsIcons.TEST_MARK)

fun Icon.multiple(): Icon {
    val compoundIcon = LayeredIcon(2)
    compoundIcon.setIcon(this, 0, 2 * iconWidth / 5, 0)
    compoundIcon.setIcon(this, 1, 0, 0)
    return compoundIcon
}

fun Icon.grayed(): Icon =
    IconUtil.filterIcon(this, {
        object : RGBImageFilter() {
            override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
                val color = Color(rgb, true)
                return ColorUtil.toAlpha(color, (color.alpha / 2.2).toInt()).rgb
            }
        }
    }, null)
