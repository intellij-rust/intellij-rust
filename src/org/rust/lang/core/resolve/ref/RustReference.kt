package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.util.RustResolveUtil

public open class RustReference<T : RustQualifiedValue>(element: T,
                                                        range: TextRange = element.textRange,
                                                        soft: Boolean = false) :
        PsiReferenceBase<T>(element, range, soft) {

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY

    protected override fun calculateDefaultRangeInElement(): TextRange? =
            TextRange.from(0, myElement.textLength)


    override fun resolve(): PsiElement? {
        return RustResolveEngine(element)
                .runFrom(RustResolveUtil.getResolveScope(element))
                .element
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    override fun getCanonicalText(): String = element.text

    override fun getRangeInElement(): TextRange? {
        return element.getSeparator().let {
            sep ->
            when (sep) {
                null -> TextRange.from(0, element.textLength)
                else -> TextRange(sep.startOffsetInParent + sep.textLength, element.textLength)
            }
        }
    }

    override fun bindToElement(element: PsiElement): PsiElement? {
        throw UnsupportedOperationException()
    }
}

