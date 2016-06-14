package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node)
                                                       , RustCompositeElement {
    override fun getReference(): RustReference? = null

}
