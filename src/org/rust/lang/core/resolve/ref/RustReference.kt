package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.psi.util.parenRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.util.RustResolveUtil

public class RustReference<T : RustPathExpr>(element: T, range: TextRange?, soft: Boolean)
    : PsiReferenceBase<T>(element, range, soft) {

    constructor(element: T) : this(element, element.parenRelativeRange, false)

    override fun getVariants(): Array<out Any>? {
        throw UnsupportedOperationException()
    }

    override fun resolve(): PsiElement? {
        return RustResolveEngine(getElement())
                    .runFrom(RustResolveUtil.getResolveScopeFor(getElement()))
                    .getElement()
    }

    protected override fun calculateDefaultRangeInElement(): TextRange? =
        myElement.parenRelativeRange

    override fun getRangeInElement(): TextRange? =
        super.getRangeInElement()
}

