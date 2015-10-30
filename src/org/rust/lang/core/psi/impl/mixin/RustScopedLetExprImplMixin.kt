package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.RustScopedLetExpr
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundVariables

abstract class RustScopedLetExprImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
        , RustScopedLetExpr {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = pat.boundVariables
}
