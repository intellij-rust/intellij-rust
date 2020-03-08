/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processAssocTypeVariants

class RsAssocTypeBindingReferenceImpl(
    element: RsAssocTypeBinding
) : RsReferenceCached<RsAssocTypeBinding>(element) {

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processAssocTypeVariants(element, it) }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsTypeAlias && element.owner.isImplOrTrait && super.isReferenceTo(element)
}
