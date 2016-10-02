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
object RustIcons {
    // Logos

    val RUST     = IconLoader.getIcon("/org/rust/ide/icons/rust.png")
    val RUST_BIG = IconLoader.getIcon("/org/rust/ide/icons/rust-big.png")

    // File types

    val RUST_FILE = IconLoader.getIcon("/org/rust/ide/icons/rust-file.png")

    // Marks

    val FINAL_MARK   = AllIcons.Nodes.FinalMark!!
    val STATIC_MARK  = AllIcons.Nodes.StaticMark!!
    val TEST_MARK    = AllIcons.Nodes.JunitTestMark!!

    // Source code elements

    val MODULE   = AllIcons.Nodes.Package!!

    val TRAIT    = AllIcons.Nodes.Interface!!
    val STRUCT   = AllIcons.Nodes.Class!!
    val TYPE     = AllIcons.Nodes.Class!!
    val IMPL     = AllIcons.Nodes.AbstractClass!!
    val ENUM     = AllIcons.Nodes.Enum!!

    val METHOD          = AllIcons.Nodes.Method!!
    val FUNCTION        = IconLoader.getIcon("/org/rust/ide/icons/nodes/function.png")
    val ASSOC_FUNCTION  = FUNCTION.addStaticMark()

    val ABSTRACT_METHOD         = AllIcons.Nodes.AbstractMethod!!
    val ABSTRACT_FUNCTION       = IconLoader.getIcon("/org/rust/ide/icons/nodes/abstractFunction.png")
    val ABSTRACT_ASSOC_FUNCTION = ABSTRACT_FUNCTION.addStaticMark()

    val MUT_ARGUMENT    = AllIcons.Nodes.Parameter!!
    val ARGUMENT        = MUT_ARGUMENT.addFinalMark()
    val FIELD           = AllIcons.Nodes.Field!!
    val MUT_BINDING     = AllIcons.Nodes.Variable!!
    val BINDING         = MUT_BINDING.addFinalMark()
    val GLOBAL_BINDING  = IconLoader.getIcon("/org/rust/ide/icons/nodes/globalBinding.png")
    val CONSTANT        = GLOBAL_BINDING.addFinalMark()
    val MUT_STATIC      = GLOBAL_BINDING.addStaticMark()
    val STATIC          = MUT_STATIC.addFinalMark()
    val ENUM_VARIANT    = FIELD.addFinalMark().addStaticMark()
}

fun Icon.addFinalMark(): Icon = LayeredIcon(this, RustIcons.FINAL_MARK)

fun Icon.addStaticMark(): Icon = LayeredIcon(this, RustIcons.STATIC_MARK)

fun Icon.addTestMark(): Icon = LayeredIcon(this, RustIcons.TEST_MARK)

fun Icon.addVisibilityIcon(pub: Boolean): RowIcon =
    RowIcon(this, if (pub) PlatformIcons.PUBLIC_ICON else PlatformIcons.PRIVATE_ICON)
