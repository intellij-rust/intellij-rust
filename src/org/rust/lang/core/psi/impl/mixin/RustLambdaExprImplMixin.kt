package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustLambdaExpr
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundVariables
import org.rust.lang.core.resolve.scope.RustResolveScope

public abstract class RustLambdaExprImplMixin(node: ASTNode) : RustNamedElementImpl(node)
        , RustLambdaExpr
        , RustResolveScope {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = patList
            .flatMap { it.boundVariables }
}

