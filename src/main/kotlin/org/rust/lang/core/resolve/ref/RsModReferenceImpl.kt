package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement

class RsModReferenceImpl(
    modDecl: RsModDeclItem
) : RsReferenceBase<RsModDeclItem>(modDecl),
    RsReference {

    override val RsModDeclItem.referenceAnchor: PsiElement get() = identifier

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processModDeclResolveVariants(element, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.referenceName) { processModDeclResolveVariants(element, it) }
}
