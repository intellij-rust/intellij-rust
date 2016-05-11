package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine


class RustQualifiedReferenceImpl(element: RustQualifiedReferenceElement, soft: Boolean = false)
    : PsiReferenceBase<RustQualifiedReferenceElement>(element, null, soft)
    , RustReference {

    override fun resolve(): RustNamedElement? =
        RustResolveEngine.resolve(element).element

    override fun getVariants(): Array<out Any> =
        RustCompletionEngine.complete(element)

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    override fun getCanonicalText(): String =
        element.let { qualRef ->
            var qual = qualRef.qualifier?.reference?.canonicalText
                              .orEmpty()

            if (qual.isNotEmpty())
                qual += RustTokenElementTypes.COLONCOLON.toString();

            qual + qualRef.name
        }

    override fun getRangeInElement(): TextRange? = element.nameElement?.parentRelativeRange

    override fun bindToElement(element: PsiElement): PsiElement? {
        throw UnsupportedOperationException()
    }
}
