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
import org.rust.lang.core.resolve.ref.ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE
import org.rust.lang.core.types.BoundElement

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
 */
class RsMacroPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsPathReference {

    override fun isReferenceToInner(element: PsiElement): Boolean =
        (element is RsMacroDefinitionBase || element is RsFunction /* proc macro */) && super.isReferenceToInner(element)

    override fun multiResolveInner(incompleteCode: Boolean): Array<out ResolveResult> {
        return resolveInner()?.let { arrayOf(PsiElementResolveResult(it)) } ?: ResolveResult.EMPTY_ARRAY
    }

    override fun multiResolveInner(): List<RsNamedElement> =
        listOfNotNull(resolveInner())

    override fun resolveInner(): RsNamedElement? {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(element, LOCAL_AND_RUST_STRUCTURE, Resolver)
    }

    fun resolveIfCached(): RsNamedElement? {
        return RsResolveCache.getInstance(element.project)
            .getCached(element, LOCAL_AND_RUST_STRUCTURE) as? RsNamedElement
    }

    private object Resolver : (RsPath) -> RsNamedElement? {
        override fun invoke(element: RsPath): RsNamedElement? {
            return pickFirstResolveVariant(element.referenceName) {
                processMacroCallPathResolveVariants(element, false, it)
            } as? RsNamedElement
        }
    }
}
