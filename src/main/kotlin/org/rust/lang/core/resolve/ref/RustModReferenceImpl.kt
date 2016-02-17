package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

class RustModReferenceImpl(modDecl: RustModDeclItem)
    : PsiReferenceBase<RustModDeclItem>(modDecl, modDecl.identifier.parentRelativeRange)
    , RustReference {

    override fun resolve(): RustModItem? =
        RustResolveEngine.resolveModDecl(element).element as RustModItem?

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
