/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryOp
import org.rust.lang.core.psi.ext.OverloadableBinaryOperator
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processBinaryOpVariants
import org.rust.lang.core.types.BoundElement

class RsBinaryOpReferenceImpl(
    element: RsBinaryOp
) : RsReferenceBase<RsBinaryOp>(element),
    RsReference {

    override val RsBinaryOp.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> {
        val operator = element.operatorType as? OverloadableBinaryOperator ?: return emptyList()
        return collectResolveVariants(operator.fnName) { processBinaryOpVariants(element, operator, it) }
    }

    override fun getVariants(): Array<out Any> = emptyArray()
}
