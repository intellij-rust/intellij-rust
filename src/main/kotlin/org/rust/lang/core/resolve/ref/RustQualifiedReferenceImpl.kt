package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.resolve.RustResolveEngine


internal class RustQualifiedReferenceImpl<T : RustQualifiedReferenceElement>(element: T,
                                                                             soft: Boolean = false)
    : PsiReferenceBase<T>(element, null, soft)
    , RustReference {

    override fun resolve(): RustNamedElement? =
        RustResolveEngine.resolve(element).element

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    override fun getCanonicalText(): String =
        element.let { qualRef ->
            var qual = qualRef.qualifier?.reference?.canonicalText
                              .orEmpty()

            if (qual.isNotEmpty())
                qual += RustTokenElementTypes.COLONCOLON.s;

            qual + qualRef.name
        }

    override fun getRangeInElement(): TextRange? =
        element.separator.let {
            sep ->
            when (sep) {
                null -> TextRange.from(0, element.textLength)
                else -> TextRange(sep.startOffsetInParent + sep.textLength, element.textLength)
            }
        }

    override fun bindToElement(element: PsiElement): PsiElement? {
        throw UnsupportedOperationException()
    }
}
