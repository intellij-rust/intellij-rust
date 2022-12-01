/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RsBinaryOp
import org.rust.lang.core.psi.ext.OverloadableBinaryOperator
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processBinaryOpVariants

class RsBinaryOpReferenceImpl(
    element: RsBinaryOp
) : RsReferenceCached<RsBinaryOp>(element) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

    override fun multiResolveUncached(): List<RsElement> {
        val operator = element.operatorType as? OverloadableBinaryOperator ?: return emptyList()
        return collectResolveVariants(operator.fnName) { processBinaryOpVariants(element, operator, it) }
    }
}
