package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustNamedElement
import javax.swing.Icon

public abstract class RustNamedElementImpl(node: ASTNode)   : RustCompositeElementImpl(node)
                                                            , RustNamedElement {

    override val nameElement: PsiElement?
        get() =
        findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? {
        return nameElement?.text
    }

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException();
    }

    override fun getNavigationElement(): PsiElement = nameElement ?: this

    override fun getTextOffset(): Int = nameElement?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getLocationString(): String? = "(in ${containingFile.name})"

        override fun getIcon(unused: Boolean): Icon? = this@RustNamedElementImpl.getIcon(0)

        override fun getPresentableText(): String? = name
    }
}
