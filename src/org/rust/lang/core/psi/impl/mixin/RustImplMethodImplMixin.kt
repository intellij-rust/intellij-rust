package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers

abstract class RustImplMethodImplMixin(node: ASTNode) : RustNamedElementImpl(node)
        , RustImplMethod {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = paramList
            .flatMap {it.pat.boundIdentifiers}
}