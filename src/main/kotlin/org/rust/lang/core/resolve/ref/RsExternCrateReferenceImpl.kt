/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement

class RsExternCrateReferenceImpl(
    externCrate: RsExternCrateItem
) : RsReferenceBase<RsExternCrateItem>(externCrate),
    RsReference {

    override val RsExternCrateItem.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processExternCrateResolveVariants(element, true, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.name!!) { processExternCrateResolveVariants(element, false, it) }
}
