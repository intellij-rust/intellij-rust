package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustEnumVariant
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon


abstract class RustEnumVariantImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustEnumVariant {
    override fun getIcon(flags: Int): Icon = RustIcons.FIELD

    override val boundElements: Collection<RustNamedElement> = listOf(this)
}
