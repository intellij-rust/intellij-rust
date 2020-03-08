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
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processDeriveTraitResolveVariants

class RsDeriveTraitReferenceImpl(
    element: RsPath
) : RsReferenceCached<RsPath>(element),
    RsPathReference {

    override fun resolveInner(): List<RsElement> {
        val traitName = element.referenceName
        return collectResolveVariants(traitName) { processDeriveTraitResolveVariants(element, traitName, it) }
    }

    override fun isReferenceTo(element: PsiElement): Boolean =
        (element is RsTraitItem || element is RsFunction) && super.isReferenceTo(element)
}
