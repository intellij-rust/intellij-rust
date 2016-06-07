package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustLambdaExprElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustLambdaExprImplMixin(node: ASTNode)    : RustNamedElementImpl(node)
                                                         , RustLambdaExprElement {

}

