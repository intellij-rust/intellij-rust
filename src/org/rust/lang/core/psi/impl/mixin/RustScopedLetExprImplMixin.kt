package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustScopeLetExpr
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustScopedLetExprImplMixin(node: ASTNode)    : RustCompositeElementImpl(node)
                                                            , RustScopeLetExpr {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        arrayListOf(scopedLetDecl)
}
