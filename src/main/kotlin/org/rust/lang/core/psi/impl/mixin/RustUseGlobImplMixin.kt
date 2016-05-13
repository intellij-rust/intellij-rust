package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustUseGlob
import org.rust.lang.core.psi.RustUseItem
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustUseGlobReferenceImpl

abstract class RustUseGlobImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustUseGlob {
    override fun getReference(): RustReference =
            RustUseGlobReferenceImpl(this)
}

val RustUseGlob.basePath: RustQualifiedReferenceElement?
    get() = parentOfType<RustUseItem>()?.let { it.pathPart }

val RustUseGlob.boundElement: RustNamedElement?
    get() = when {
        alias != null      -> alias
        identifier != null -> this
        self != null       -> basePath
        else               -> null
    }
