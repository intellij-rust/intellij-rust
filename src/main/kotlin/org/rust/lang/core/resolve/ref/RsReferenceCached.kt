/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement

abstract class RsReferenceCached<T : RsReferenceElement>(
    element: T
) : RsReferenceBase<T>(element),
    RsReference {

    abstract protected fun resolveInner(): List<RsElement>

    final override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        cachedMultiResolve().toTypedArray()

    final override fun multiResolve(): List<RsNamedElement> =
        cachedMultiResolve().mapNotNull { it.element as? RsNamedElement }

    private fun cachedMultiResolve(): List<PsiElementResolveResult> {
        return ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver,
                /* needToPreventRecursion = */ true,
                /* incompleteCode = */ false).orEmpty()
    }

    private object Resolver : ResolveCache.AbstractResolver<RsReferenceCached<*>, List<PsiElementResolveResult>> {
        override fun resolve(ref: RsReferenceCached<*>, incompleteCode: Boolean): List<PsiElementResolveResult> {
            return ref.resolveInner().map { PsiElementResolveResult(it) }
        }
    }
}
