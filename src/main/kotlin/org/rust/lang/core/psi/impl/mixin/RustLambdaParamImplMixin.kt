package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustLambdaParam
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundElements

public abstract class RustLambdaParamImplMixin(node: ASTNode)   : RustCompositeElementImpl(node)
                                                                , RustLambdaParam {

    override val boundElements: Collection<RustNamedElement>
        get() = pat.boundElements
}
