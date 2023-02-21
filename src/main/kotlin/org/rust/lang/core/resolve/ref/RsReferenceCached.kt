/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsReferenceElement

abstract class RsReferenceCached<T : RsReferenceElement>(
    element: T
) : RsReferenceBase<T>(element) {

    protected abstract fun multiResolveUncached(): List<RsElement>

    final override fun multiResolveInner(incompleteCode: Boolean): Array<out ResolveResult> =
        cachedMultiResolve().toTypedArray()

    final override fun multiResolveInner(): List<RsElement> =
        cachedMultiResolve().mapNotNull { it.element as? RsElement }

    private fun cachedMultiResolve(): List<PsiElementResolveResult> {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(element, cacheDependency, Resolver).orEmpty()
    }

    protected open val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

    private object Resolver : (RsReferenceElement) -> List<PsiElementResolveResult> {
        override fun invoke(ref: RsReferenceElement): List<PsiElementResolveResult> {
            return (ref.reference as RsReferenceCached<*>).multiResolveUncached().map { PsiElementResolveResult(it) }
        }
    }
}
