package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatVar
import org.rust.lang.core.psi.impl.RustPatImpl

public abstract class RustPatVarImplMixin(node: ASTNode) : RustPatImpl(node)
        , RustPatVar {

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException()
    }

    override fun getName(): String? = identifier.text

    override fun getNavigationElement(): PsiElement = identifier
}