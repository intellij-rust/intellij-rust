package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processLifetimeResolveVariants
import org.rust.lang.core.resolve.processLabelResolveVariants
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.resolve.processExternCrateResolveVariants
import org.rust.lang.core.resolve.processModDeclResolveVariants
import org.rust.lang.core.resolve.processUseGlobResolveVariants
import org.rust.lang.core.resolve.processMethodCallExprResolveVariants
import org.rust.lang.core.resolve.processStructLiteralFieldResolveVariants
import org.rust.lang.core.resolve.processFieldExprResolveVariants

class RsLifetimeReferenceImpl(
    element: RsLifetime
) : RsReferenceBase<RsLifetime>(element),
    RsReference {

    override val RsLifetime.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) { processLifetimeResolveVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processLifetimeResolveVariants(element, it) }
}
