package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustTypeParamElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustTypeParamImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustTypeParamElement {
    override val boundElements: Collection<RustNamedElement> get() = listOf(this)
}
