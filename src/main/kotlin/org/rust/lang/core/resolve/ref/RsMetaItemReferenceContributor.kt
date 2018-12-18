/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.*
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
) : RsReferenceCached<RsMetaItem>(element) {

    override val RsMetaItem.referenceAnchor: PsiElement? get() = element.identifier

    override fun resolveInner(): List<RsElement> {
        val traitName = element.name ?: return emptyList()
        return collectResolveVariants(traitName) { processDeriveTraitResolveVariants(element, traitName, it) }
    }
}
