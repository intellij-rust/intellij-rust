package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustTokenElementTypes

abstract class RustNamedElementImpl(node: ASTNode) : RustCompositeElementImpl(node),
                                                     RustNamedElement,
                                                     PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RustPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
