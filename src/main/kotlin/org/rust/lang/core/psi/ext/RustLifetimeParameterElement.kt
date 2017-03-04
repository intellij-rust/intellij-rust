package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsLifetimeParameter

abstract class RsLifetimeParameterImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsLifetimeParameter {

    override fun getNameIdentifier() = lifetimeDecl.quoteIdentifier

}
