package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustImplMethodImplMixin(node: ASTNode)   : RustNamedElementImpl(node)
                                                        , RustImplMethod {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        paramList.orEmpty().filterNotNull()
}