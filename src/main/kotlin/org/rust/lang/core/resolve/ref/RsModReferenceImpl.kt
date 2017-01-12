package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsModReferenceImpl(
    modDecl: RsModDeclItem
) : RsReferenceBase<RsModDeclItem>(modDecl),
    RsReference {

    override val RsModDeclItem.referenceAnchor: PsiElement get() = identifier

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(ResolveEngine.resolveModDecl(element))

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
