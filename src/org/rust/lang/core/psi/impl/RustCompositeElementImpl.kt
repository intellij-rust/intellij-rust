package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType

import org.rust.lang.core.lexer.RustTokenElementTypes.*;
import org.rust.lang.core.psi.RustCompositeElement

public open class RustCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RustCompositeElement {

}