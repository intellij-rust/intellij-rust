/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processAssocTypeVariants

class RsAssocTypeBindingReferenceImpl(
    element: RsAssocTypeBinding
) : RsReferenceCached<RsAssocTypeBinding>(element) {
    override val RsAssocTypeBinding.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processAssocTypeVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processAssocTypeVariants(element, it) }
}
