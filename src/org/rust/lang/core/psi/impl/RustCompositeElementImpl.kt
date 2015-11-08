package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustCompositeElement

public open class RustCompositeElementImpl(node: ASTNode)   : ASTWrapperPsiElement(node)
                                                            , RustCompositeElement {

}
