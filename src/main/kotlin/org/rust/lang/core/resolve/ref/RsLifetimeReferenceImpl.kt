/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processLifetimeResolveVariants

class RsLifetimeReferenceImpl(
    element: RsLifetime
) : RsReferenceCached<RsLifetime>(element),
    RsReference {

    override val RsLifetime.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processLifetimeResolveVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processLifetimeResolveVariants(element, it) }
}
