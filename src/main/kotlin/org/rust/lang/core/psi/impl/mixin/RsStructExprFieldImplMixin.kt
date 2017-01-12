package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsStructExprFieldReferenceImpl

abstract class RsStructExprFieldImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsStructExprField {

    override fun getReference(): RsReference = RsStructExprFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text
}

