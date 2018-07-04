/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement

abstract class RsReferenceCached<T : RsWeakReferenceElement>(
    element: T
) : RsReferenceBase<T>(element),
    RsReference {

    protected abstract fun resolveInner(): List<RsElement>

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        cachedMultiResolve().toTypedArray()

    final override fun multiResolve(): List<RsNamedElement> =
        cachedMultiResolve().mapNotNull { it.element as? RsNamedElement }

    private fun cachedMultiResolve(): List<PsiElementResolveResult> {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver).orEmpty()
    }

    private object Resolver : (RsReferenceCached<*>) -> List<PsiElementResolveResult> {
        override fun invoke(ref: RsReferenceCached<*>): List<PsiElementResolveResult> {
            return ref.resolveInner().map { PsiElementResolveResult(it) }
        }
    }
}
