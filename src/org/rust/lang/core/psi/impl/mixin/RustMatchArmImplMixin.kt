package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustMatchArm
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers
import org.rust.lang.core.resolve.scope.RustResolveScope

abstract class RustMatchArmImplMixin(node: ASTNode): RustCompositeElementImpl(node)
        , RustMatchArm
        , RustResolveScope {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = patList
            .flatMap {it.boundIdentifiers}
}