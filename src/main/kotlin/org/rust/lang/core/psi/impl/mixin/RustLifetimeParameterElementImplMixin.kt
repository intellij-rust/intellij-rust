package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RsLifetimeParameterImplMixin(node: ASTNode) : RustNamedElementImpl(node), RsLifetimeParameter {

    override fun getNameIdentifier() = lifetime

}
