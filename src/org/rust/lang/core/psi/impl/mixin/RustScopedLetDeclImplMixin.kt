package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustScopedLetDecl
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.boundIdentifiers

public abstract class RustScopedLetDeclImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                                , RustScopedLetDecl {

    override fun getBoundElements(): Collection<RustNamedElement> =
        pat.boundIdentifiers
}

