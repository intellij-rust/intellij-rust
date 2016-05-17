package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustFieldName
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustFieldReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustFieldNameImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustFieldName {
    override fun getReference(): RustReference = RustFieldReferenceImpl(this)
}

