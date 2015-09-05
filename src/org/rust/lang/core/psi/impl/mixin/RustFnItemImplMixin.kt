package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustExprPath
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.scope.RustResolveScope

public abstract class RustFnItemImplMixin(node: ASTNode)
    : RustCompositeElementImpl(node)
    , RustFnItem
    , RustResolveScope {

    override fun lookup(path: RustExprPath): RustNamedElement? {
        return super<RustResolveScope>.lookup(path)
    }
}

