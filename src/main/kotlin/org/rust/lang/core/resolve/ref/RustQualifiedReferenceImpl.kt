package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine


class RustQualifiedReferenceImpl(element: RustQualifiedReferenceElement)
    : RustReferenceBase<RustQualifiedReferenceElement>(element, element.nameElement)
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
}
