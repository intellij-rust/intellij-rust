package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.CompletionProcessor
import org.rust.lang.core.resolve.MultiResolveProcessor
import org.rust.lang.core.resolve.ResolveConfig
import org.rust.lang.core.resolve.processResolveVariants

class RsFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RsReferenceBase<RsFieldExpr>(fieldExpr),
    RsReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val p = CompletionProcessor()
        processResolveVariants(element, ResolveConfig(isCompletion = true), p)
        return p.result.toTypedArray()
    }

    override fun resolveInner(): List<RsCompositeElement> {
        val p = MultiResolveProcessor(element.referenceName)
        processResolveVariants(element, ResolveConfig(isCompletion = false), p)
        return p.result
    }

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}
