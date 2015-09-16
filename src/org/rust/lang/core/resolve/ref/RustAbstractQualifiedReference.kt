package org.rust.lang.core.resolve.ref

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.util.RustResolveUtil

public abstract class RustAbstractQualifiedReference(node: ASTNode) : ASTWrapperPsiElement(node)
                                                                    , RustQualifiedReference {

    override fun getElement(): PsiElement = this

    override fun getReference(): PsiReference = this

    override fun resolve(): PsiElement? {
        return RustResolveEngine(this)
                    .runFrom(RustResolveUtil.getResolveScope(getElement()))
                    .getElement()
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        resolve().let {
            target -> return getManager().areElementsEquivalent(target, element)
        }
    }

    override fun getCanonicalText(): String = getText()

    override fun getRangeInElement(): TextRange? {
        return getSeparator().let {
            sep ->
                when (sep) {
                    null -> TextRange.from(0, getTextLength())
                    else -> TextRange(sep.getStartOffsetInParent() + sep.getTextLength(), getTextLength())
                }
        }
    }

    override fun getReferenceName(): String? =
        getReferenceNameElement()?.let { e -> e.getText().trim() }

    override fun getQualifier(): RustAbstractQualifiedReference? = findChildByClass(javaClass)

    override fun handleElementRename(newElementName: String?): PsiElement? {
        throw UnsupportedOperationException()
    }

    override fun bindToElement(element: PsiElement): PsiElement? {
        throw UnsupportedOperationException()
    }

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException()
    }
}

