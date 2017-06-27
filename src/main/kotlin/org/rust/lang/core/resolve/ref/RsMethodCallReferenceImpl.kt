/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement

class RsMethodCallReferenceImpl(
    element: RsMethodCallExpr
) : RsReferenceBase<RsMethodCallExpr>(element),
    RsReference {

    override val RsMethodCallExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processMethodCallExprResolveVariants(element, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.referenceName) { processMethodCallExprResolveVariants(element, it) }
}
