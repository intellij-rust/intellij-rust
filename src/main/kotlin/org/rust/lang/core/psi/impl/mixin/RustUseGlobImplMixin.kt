package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustUseGlobReferenceImpl

abstract class RustUseGlobImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustUseGlobElement {
    override fun getReference(): RustReference =
            RustUseGlobReferenceImpl(this)
}

val RustUseGlobElement.basePath: RustQualifiedReferenceElement?
    get() = parentOfType<RustUseItemElement>()?.let { it.path }

val RustUseGlobElement.boundElement: RustNamedElement?
    get() = when {
        alias != null      -> alias
        identifier != null -> this
        self != null       -> basePath
        else               -> null
    }
