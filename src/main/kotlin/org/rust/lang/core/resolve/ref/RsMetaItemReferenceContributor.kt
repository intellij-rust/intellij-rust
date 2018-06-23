/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processDeriveTraitResolveVariants

class RsMetaItemReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(RsPsiPattern.derivedTraitMetaItem, RsDeriveTraitReferenceProvider())
    }
}

private class RsDeriveTraitReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val metaItem = element as? RsMetaItem ?: return emptyArray()
        return arrayOf(RsDeriveTraitReferenceImpl(metaItem))
    }
}

private class RsDeriveTraitReferenceImpl(
    element: RsMetaItem
) : PsiPolyVariantReferenceBase<RsMetaItem>(element) {

    override fun calculateDefaultRangeInElement(): TextRange {
        val identifier = element.identifier ?: return TextRange.EMPTY_RANGE
        return TextRange.from(identifier.startOffsetInParent, identifier.textLength)
    }

    // Completion in `#[derive]` attribute is provided by `RsDeriveCompletionProvider`
    override fun getVariants(): Array<Any> = emptyArray()

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        return ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver,
                /* needToPreventRecursion = */ true,
                /* incompleteCode = */ false)
            .orEmpty().toTypedArray()
    }

    private fun resolveInner(): List<RsElement> {
        val traitName = element.name ?: return emptyList()
        return collectResolveVariants(traitName) { processDeriveTraitResolveVariants(element, traitName, it) }
    }

    private object Resolver : ResolveCache.AbstractResolver<RsDeriveTraitReferenceImpl, List<PsiElementResolveResult>> {
        override fun resolve(ref: RsDeriveTraitReferenceImpl, incompleteCode: Boolean): List<PsiElementResolveResult> {
            return ref.resolveInner().map { PsiElementResolveResult(it) }
        }
    }
}
