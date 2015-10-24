package org.rust.lang.core.resolve.ref

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.util.RustResolveUtil

public abstract class RustAbstractQualifiedReference(node: ASTNode) : ASTWrapperPsiElement(node)
                                                                    , RustQualifiedReference {

    override fun getElement(): PsiElement = this

    override fun getReference(): PsiReference = this

    override fun resolve(): PsiElement? {
        return RustResolveEngine(this)
                    .runFrom(RustResolveUtil.getResolveScope(element))
                    .element
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        resolve().let {
            target -> return manager.areElementsEquivalent(target, element)
        }
    }

    override fun getCanonicalText(): String = text

    override fun getRangeInElement(): TextRange? {
        return getSeparator().let {
            sep ->
                when (sep) {
                    null -> TextRange.from(0, textLength)
                    else -> TextRange(sep.startOffsetInParent + sep.textLength, textLength)
                }
        }
    }

    override fun getReferenceName(): String? =
        getReferenceNameElement()?.let { e -> e.text.trim() }

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

