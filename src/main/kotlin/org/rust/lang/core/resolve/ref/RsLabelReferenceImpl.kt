/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processLabelResolveVariants

class RsLabelReferenceImpl(
    element: RsLabel
) : RsReferenceCached<RsLabel>(element),
    RsReference {

    override val RsLabel.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processLabelResolveVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processLabelResolveVariants(element, it) }
}
