/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processExternCrateResolveVariants

class RsExternCrateReferenceImpl(
    externCrate: RsExternCrateItem
) : RsReferenceCached<RsExternCrateItem>(externCrate),
    RsReference {

    override val RsExternCrateItem.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processExternCrateResolveVariants(element, false, it) }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsFile && super.isReferenceTo(element)
}
