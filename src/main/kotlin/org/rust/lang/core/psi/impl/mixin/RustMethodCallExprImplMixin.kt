package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.impl.RustExprElementImpl
import org.rust.lang.core.resolve.ref.RustMethodCallReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustMethodCallExprImplMixin(node: ASTNode?) : RustExprElementImpl(node), RustMethodCallExprElement {
    override fun getReference(): RustReference = RustMethodCallReferenceImpl(this)
}
