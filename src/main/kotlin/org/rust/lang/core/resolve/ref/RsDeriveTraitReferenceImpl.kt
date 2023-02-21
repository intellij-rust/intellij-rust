/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.isKnownDerivable
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processDeriveTraitResolveVariants
import org.rust.lang.core.resolve.processProcMacroResolveVariants

class RsDeriveTraitReferenceImpl(
    element: RsPath
) : RsReferenceCached<RsPath>(element),
    RsPathReference {

    override fun multiResolveUncached(): List<RsElement> {
        // We resolve standard derive proc macros, such as Derive or Display,
        // to their corresponding traits,
        // because resolving them to macros makes little sense to users (these macros are empty)
        element.resolveToDerivedTrait()
            .filter { it.isKnownDerivable }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        return element.resolveToProcMacro()
    }

    override fun isReferenceToInner(element: PsiElement): Boolean =
        (element is RsTraitItem || element is RsFunction) && super.isReferenceToInner(element)
}

private fun RsPath.resolveToDerivedTrait(): List<RsTraitItem> {
    val traitName = referenceName ?: return emptyList()
    val variants = collectResolveVariants(traitName) {
        processDeriveTraitResolveVariants(this, traitName, it)
    }
    return variants.filterIsInstance<RsTraitItem>()
}

private fun RsPath.resolveToProcMacro(): List<RsElement> {
    val traitName = referenceName ?: return emptyList()
    return collectResolveVariants(traitName) {
        processProcMacroResolveVariants(this, it, isCompletion = false)
    }
}
