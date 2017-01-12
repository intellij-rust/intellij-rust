package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.resolve.ref.RustReference

abstract class RsCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node),
                                                       RsCompositeElement {
    override fun getReference(): RustReference? = null

}
