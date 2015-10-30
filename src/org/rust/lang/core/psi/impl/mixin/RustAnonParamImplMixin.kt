package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustAnonParam
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundElements

public abstract class RustAnonParamImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                            , RustAnonParam {

    override fun getBoundElements() : Collection<RustNamedElement> =
        pat?.boundElements.orEmpty()
}

