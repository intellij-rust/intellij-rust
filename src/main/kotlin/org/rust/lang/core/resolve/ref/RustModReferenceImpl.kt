package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustModReferenceImpl(
    modDecl: RustModDeclItemElement
) : RustReferenceBase<RustModDeclItemElement>(modDecl),
    RustReference {

    override val RustModDeclItemElement.referenceAnchor: PsiElement get() = identifier

    override fun resolveInner(): List<RustNamedElement> = listOfNotNull(RustResolveEngine.resolveModDecl(element))

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
