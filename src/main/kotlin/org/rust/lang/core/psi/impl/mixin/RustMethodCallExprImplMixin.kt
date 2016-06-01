package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustMethodCallExpr
import org.rust.lang.core.psi.impl.RustExprImpl
import org.rust.lang.core.resolve.ref.RustMethodCallReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustMethodCallExprImplMixin(node: ASTNode?) : RustExprImpl(node), RustMethodCallExpr {
    override fun getReference(): RustReference = RustMethodCallReferenceImpl(this)
}
