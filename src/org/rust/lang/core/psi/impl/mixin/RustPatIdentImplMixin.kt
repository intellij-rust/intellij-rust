package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.psi.impl.RustNamedElementImpl

public abstract class RustPatIdentImplMixin(node: ASTNode)  : RustNamedElementImpl(node)
                                                            , RustPatIdent {

    override fun getNavigationElement(): PsiElement = identifier

}

