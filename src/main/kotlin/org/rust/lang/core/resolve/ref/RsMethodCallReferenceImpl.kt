package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.CompletionProcessor
import org.rust.lang.core.resolve.MultiResolveProcessor
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.resolve.processResolveVariants

class RsMethodCallReferenceImpl(
    element: RsMethodCallExpr
) : RsReferenceBase<RsMethodCallExpr>(element),
    RsReference {

    override val RsMethodCallExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val p = CompletionProcessor()
        processResolveVariants(element, p)
        return p.result.toTypedArray()
    }

    override fun resolveInner(): List<RsCompositeElement> {
        val p = MultiResolveProcessor(element.referenceName)
        processResolveVariants(element, p)
        return p.result
    }
}
