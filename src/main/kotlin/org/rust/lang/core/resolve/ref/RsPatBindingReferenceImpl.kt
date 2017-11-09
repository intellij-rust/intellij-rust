/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processPatBindingResolveVariants


class RsPatBindingReferenceImpl(
    element: RsPatBinding
) : RsReferenceCached<RsPatBinding>(element),
    RsReference {

    override val RsPatBinding.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processPatBindingResolveVariants(element, false, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processPatBindingResolveVariants(element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }
}
