package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.util.parentRelativeRange

abstract class RustReferenceBase<T : RustCompositeElement>(
    element: T,
    refIdentifier: PsiElement
) : PsiReferenceBase<T>(element, refIdentifier.parentRelativeRange) {
    init {
        check(refIdentifier.parent == element)
    }

    // enforce not nullability
    final override fun getRangeInElement(): TextRange = super.getRangeInElement()
}
