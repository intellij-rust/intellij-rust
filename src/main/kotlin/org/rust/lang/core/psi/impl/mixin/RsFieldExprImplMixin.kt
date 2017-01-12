package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.impl.RsExprImpl
import org.rust.lang.core.resolve.ref.RustFieldExprReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RsFieldExprImplMixin(node: ASTNode?) : RsExprImpl(node), RsFieldExpr {
    override val referenceNameElement: PsiElement get() = fieldId

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RustReference = RustFieldExprReferenceImpl(this)

}
