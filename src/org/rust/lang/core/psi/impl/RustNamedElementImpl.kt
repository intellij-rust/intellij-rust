package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustNamedElement

public abstract class RustNamedElementImpl(node: ASTNode) : RustCompositeElementImpl(node), RustNamedElement {

    override fun getName(): String? {
        return findChildByType<PsiElement>(RustTokenElementTypes.IDENTIFIER)?.text
    }

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException();
    }
}