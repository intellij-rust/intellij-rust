package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers
import org.rust.lang.core.resolve.scope.RustResolveScope

public abstract class RustFnItemImplMixin(node: ASTNode)
    : RustNamedElementImpl(node)
    , RustFnItem
    , RustResolveScope {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = fnParams
            ?.paramList.orEmpty()
            .flatMap { it.pat.boundIdentifiers }
}

