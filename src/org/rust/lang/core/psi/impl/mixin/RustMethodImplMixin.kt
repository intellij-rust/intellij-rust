package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustMethod
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers

abstract class RustMethodImplMixin(node: ASTNode) : RustNamedElementImpl(node)
        , RustMethod {

    override fun listDeclarations(before: PsiElement): List<RustPatIdent> = anonParams?.
            anonParamList.orEmpty()
            .flatMap { it.pat?.boundIdentifiers.orEmpty() }

}