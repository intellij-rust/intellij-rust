package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.util.elementType

abstract class RsNamedElementImpl(node: ASTNode) : RsCompositeElementImpl(node),
                                                   RsNamedElement,
                                                   PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.let {
            it.replace(RsPsiFactory(project).createIdentifier(name, it.elementType))
        }
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
