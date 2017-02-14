package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.impl.RsNamedElementImpl

abstract class RsLabelDeclImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsLabelDecl {
    override fun getNameIdentifier(): PsiElement? = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }
}
