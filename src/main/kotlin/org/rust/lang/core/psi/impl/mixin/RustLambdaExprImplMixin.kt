package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustLambdaExpr
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustLambdaExprImplMixin(node: ASTNode)    : RustNamedElementImpl(node)
                                                         , RustLambdaExpr {

    override val declarations: List<RustDeclaringElement>
        get() = lambdaParamList.orEmpty()
}

