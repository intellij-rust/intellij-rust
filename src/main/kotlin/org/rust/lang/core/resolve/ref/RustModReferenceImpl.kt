package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustModReferenceImpl(
    modDecl: RsModDeclItem
) : RustReferenceBase<RsModDeclItem>(modDecl),
    RustReference {

    override val RsModDeclItem.referenceAnchor: PsiElement get() = identifier

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(RustResolveEngine.resolveModDecl(element))

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
