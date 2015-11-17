package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustNamedElement

public abstract class RustNamedElementImpl(node: ASTNode)   : RustCompositeElementImpl(node)
                                                            , RustNamedElement {

    override fun getNameElement(): PsiElement? =
        findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? {
        return getNameElement()?.text
    }

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException();
    }

    override fun getNavigationElement() = getNameElement()

    override fun getTextOffset(): Int = getNameElement()?.textOffset ?: super.getTextOffset()
}
