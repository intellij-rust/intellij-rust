package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroBodySimpleMatching
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processMacroSimpleResolveVariants
import org.rust.lang.core.types.BoundElement


class RsMacroBodySimpleMatchingReferenceImpl(pattern: RsMacroBodySimpleMatching) : RsReferenceBase<RsMacroBodySimpleMatching>(pattern) {
    override val RsMacroBodySimpleMatching.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processMacroSimpleResolveVariants(element, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>>
        = collectResolveVariants(element.referenceName) { processMacroSimpleResolveVariants(element, it) }
}
