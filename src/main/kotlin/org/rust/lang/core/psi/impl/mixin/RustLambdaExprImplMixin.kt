package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustLambdaExprElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustLambdaExprImplMixin(node: ASTNode)    : RustNamedElementImpl(node)
                                                         , RustLambdaExprElement {

    override val declarations: List<RustDeclaringElement>
        get() = parameters.parameterList.orEmpty()
}

