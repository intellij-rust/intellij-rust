package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.scope.RustResolveScope

public abstract class RustFnItemImplMixin(node: ASTNode)
    : RustNamedElementImpl(node)
    , RustFnItem
    , RustResolveScope {

}

