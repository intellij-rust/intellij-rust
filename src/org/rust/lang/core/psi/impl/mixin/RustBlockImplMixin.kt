package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustBlock
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.scope.RustResolveScope

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustBlock, RustResolveScope {
}