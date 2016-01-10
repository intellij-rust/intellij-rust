package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatBinding
import org.rust.lang.core.psi.impl.RustNamedElementImpl

public abstract class RustPatBindingImplMixin(node: ASTNode)  : RustNamedElementImpl(node)
                                                              , RustPatBinding {

    override fun getNavigationElement(): PsiElement = identifier

    override val boundElements: Collection<RustNamedElement>
        get() = listOf(this)
}

