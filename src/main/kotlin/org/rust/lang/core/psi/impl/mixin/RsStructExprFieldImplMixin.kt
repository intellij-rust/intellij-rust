package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.impl.RsCompositeElementImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustStructExprFieldReferenceImpl

abstract class RsStructExprFieldImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsStructExprField {

    override fun getReference(): RustReference = RustStructExprFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text
}

