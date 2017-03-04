package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.impl.RsExprImpl
import org.rust.lang.core.resolve.ref.RsMethodCallReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMethodCallExprImplMixin(node: ASTNode?) : RsExprImpl(node), RsMethodCallExpr {
    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text

    override fun getReference(): RsReference = RsMethodCallReferenceImpl(this)

}
