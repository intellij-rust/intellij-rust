package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustSelfArgumentElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustSelfArgumentImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustSelfArgumentElement {
    override val nameElement: PsiElement get() = self

    override fun setName(name: String): PsiElement? {
        // can't rename self
        throw UnsupportedOperationException()
    }

    override val boundElements: Collection<RustNamedElement> = listOf(this)
}
