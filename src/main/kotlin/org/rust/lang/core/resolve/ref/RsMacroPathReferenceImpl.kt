/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsMacroDefinitionBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.pickFirstResolveVariant
import org.rust.lang.core.resolve.processMacroCallPathResolveVariants
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl.Companion.resolveInBatchMode
import org.rust.lang.core.types.BoundElement
import org.rust.stdext.ThreadLocalDelegate

/**
 * A reference for path of macro call:
 * ```rust
 * foo!();
 * //^ this path
 * foo::bar!();
 *     //^ or this path
 * ```
 *
 * Some differences from [RsPathReferenceImpl]:
 * 1. It always points to a macro (declarative of procedural)
 * 2. Macro path can't have generic arguments, so we don't store [BoundElement] to the cache (to save memory)
 * 3. In the current implementation macros can't be multiresolved, so we store single nullable element in
 * |  the cache instead of a list (to save memory)
 * 4. This reference introduces "batch mode" ([resolveInBatchMode]) in which another cache dependency is used.
 * |  See [ResolveCacheDependency.MACRO] for more info
 */
class RsMacroPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsPathReference {

    override fun isReferenceTo(element: PsiElement): Boolean =
        (element is RsMacroDefinitionBase || element is RsFunction /* proc macro */) && super.isReferenceTo(element)

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
        return resolve()?.let { arrayOf(PsiElementResolveResult(it)) } ?: ResolveResult.EMPTY_ARRAY
    }

    override fun multiResolve(): List<RsNamedElement> =
        listOfNotNull(resolve())

    override fun resolve(): RsNamedElement? {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(element, cacheDep, Resolver)
    }

    fun resolveIfCached(): RsNamedElement? {
        return RsResolveCache.getInstance(element.project)
            .getCached(element, cacheDep) as? RsNamedElement
    }

    private object Resolver : (RsPath) -> RsNamedElement? {
        override fun invoke(element: RsPath): RsNamedElement? {
            return pickFirstResolveVariant(element.referenceName) {
                processMacroCallPathResolveVariants(element, false, it)
            } as? RsNamedElement
        }
    }

    companion object {
        val cacheDep: ResolveCacheDependency
            get() = if (isBatchMode) ResolveCacheDependency.MACRO else ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

        var isBatchMode by ThreadLocalDelegate { false }

        /** @see ResolveCacheDependency.MACRO */
        inline fun <T> resolveInBatchMode(action: () -> T): T {
            isBatchMode = true
            return try {
                action()
            } finally {
                isBatchMode = false
            }
        }
    }
}
