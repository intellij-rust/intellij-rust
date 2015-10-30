package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustMatchPat
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers

public abstract class RustMatchPatImplMixin(node: ASTNode)  : RustCompositeElementImpl(node)
                                                            , RustMatchPat {

    override fun getBoundElements(): Collection<RustNamedElement> =
        patList .flatMap { it.boundIdentifiers }
                .filterNotNull()
}

