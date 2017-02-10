package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.impl.RsNamedElementImpl

abstract class RsLifetimeParameterImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsLifetimeParameter {

    override fun getNameIdentifier() = lifetimeDecl.lifetime

}
