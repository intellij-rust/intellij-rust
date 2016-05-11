package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustTypeParam
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustTypeParamImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustTypeParam {
    override val boundElements: Collection<RustNamedElement> get() = listOf(this)
}
