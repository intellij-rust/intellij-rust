package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustLifetimeParameterElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustLifetimeParameterElementImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustLifetimeParameterElement {

    override fun getNameIdentifier() = lifetime

}
