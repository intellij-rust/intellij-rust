package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustUseGlob
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustUseGlobReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustUseGlobImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustUseGlob {
    override fun getReference(): RustReference =
            RustUseGlobReferenceImpl(this)
}
