package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustLifetimeParamElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustLifetimeParamElementImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustLifetimeParamElement {

    override fun getNameIdentifier() = lifetime

}
