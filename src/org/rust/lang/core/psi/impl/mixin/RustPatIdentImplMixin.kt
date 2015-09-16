package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.impl.RustPatImpl

public abstract class RustPatIdentImplMixin(node: ASTNode) : RustPatImpl(node)
                                                           , RustPatIdent
                                                           , RustNamedElement {

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException()
    }

    override fun getName(): String? = getIdentifier().getText()

    override fun getNavigationElement(): PsiElement? = getIdentifier()
}