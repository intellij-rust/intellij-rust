package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustStructExprFieldElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustStructExprFieldReferenceImpl

abstract class RustStructExprFieldImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustStructExprFieldElement {

    override fun getReference(): RustReference = RustStructExprFieldReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier
}

