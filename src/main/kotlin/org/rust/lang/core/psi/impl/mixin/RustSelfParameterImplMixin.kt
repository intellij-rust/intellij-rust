package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustSelfParameterElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustSelfParameterImplMixin(node: ASTNode) : RustNamedElementImpl(node), RustSelfParameterElement {
    override fun getNameIdentifier(): PsiElement = self

    override fun setName(name: String): PsiElement? {
        // can't rename self
        throw UnsupportedOperationException()
    }

    override fun getIcon(flags: Int) = RustIcons.ARGUMENT
}
