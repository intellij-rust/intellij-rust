package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsStructExpr
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsStructExprFieldReferenceImpl


val RsStructExprField.parentStructExpr: RsStructExpr get() = parentOfType<RsStructExpr>()!!

abstract class RsStructExprFieldImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsStructExprField {

    override fun getReference(): RsReference = RsStructExprFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text
}

