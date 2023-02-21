/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processLifetimeResolveVariants

class RsLifetimeReferenceImpl(
    element: RsLifetime
) : RsReferenceCached<RsLifetime>(element) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

    override fun multiResolveUncached(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processLifetimeResolveVariants(element, it) }

    override fun isReferenceToInner(element: PsiElement): Boolean =
        (element is RsLifetimeParameter || element is RsLifetime) && super.isReferenceToInner(element)
}
