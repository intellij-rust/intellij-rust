/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement

abstract class RsReferenceCached<T : RsWeakReferenceElement>(
    element: T
) : RsReferenceBase<T>(element),
    RsReference {

    protected abstract fun resolveInner(): List<RsElement>

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        cachedMultiResolve().toTypedArray()

    final override fun multiResolve(): List<RsElement> =
        cachedMultiResolve().mapNotNull { it.element as? RsElement }

    private fun cachedMultiResolve(): List<PsiElementResolveResult> {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(element, cacheDependency, Resolver).orEmpty()
    }

    protected open val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.RUST_STRUCTURE

    private object Resolver : (RsWeakReferenceElement) -> List<PsiElementResolveResult> {
        override fun invoke(ref: RsWeakReferenceElement): List<PsiElementResolveResult> {
            return (ref.reference as RsReferenceCached<*>).resolveInner().map { PsiElementResolveResult(it) }
        }
    }
}
