package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsLabelReferenceImpl(
    element: RsLabel
) : RsReferenceBase<RsLabel>(element),
    RsReference {

    override val RsLabel.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(ResolveEngine.resolveLabel(element))

    override fun getVariants(): Array<out Any> = emptyArray()
}
