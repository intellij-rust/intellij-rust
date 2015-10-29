package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustMethod
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundVariables

abstract class RustMethodImplMixin(node: ASTNode) : RustNamedElementImpl(node)
        , RustMethod {

    override fun listDeclarations(before: PsiElement): List<RustPatVar> = anonParams?.
            anonParamList.orEmpty()
            .flatMap { it.pat?.boundVariables.orEmpty() }

}
