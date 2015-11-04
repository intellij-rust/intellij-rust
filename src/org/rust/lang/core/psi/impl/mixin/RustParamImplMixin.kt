package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustParam
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.boundElements

public abstract class RustParamImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                        , RustParam {

    override fun getBoundElements() : Collection<RustNamedElement> =
        pat.boundElements
}

