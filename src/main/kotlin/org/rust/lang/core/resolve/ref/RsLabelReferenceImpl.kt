/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processLabelResolveVariants

class RsLabelReferenceImpl(
    element: RsLabel
) : RsReferenceCached<RsLabel>(element) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL

    override fun multiResolveUncached(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processLabelResolveVariants(element, it) }

    override fun isReferenceToInner(element: PsiElement): Boolean =
        element is RsLabelDecl && super.isReferenceToInner(element)
}
