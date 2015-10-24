package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.util.RustResolveUtil

public class RustReference<T : RustQualifiedReference>(element: T, range: TextRange?, soft: Boolean)
    : PsiReferenceBase<T>(element, range, soft) {

    constructor(element: T) : this(element, element.rangeInElement, false)

    override fun getVariants(): Array<out Any> {
        throw UnsupportedOperationException()
    }

    override fun resolve(): PsiElement? {
        return RustResolveEngine(element)
                    .runFrom(RustResolveUtil.getResolveScope(element))
                    .element
    }

    protected override fun calculateDefaultRangeInElement(): TextRange? =
        TextRange.from(0, myElement.textLength)
}

