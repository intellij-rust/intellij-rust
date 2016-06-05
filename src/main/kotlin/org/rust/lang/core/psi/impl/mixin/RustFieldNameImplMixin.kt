package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustFieldNameElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustStructExprFieldReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustFieldNameImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustFieldNameElement {

    override fun getReference(): RustReference = RustStructExprFieldReferenceImpl(this)

}

