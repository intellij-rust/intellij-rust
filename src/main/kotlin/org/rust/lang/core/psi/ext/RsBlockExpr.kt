/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.stubs.RsBlockExprStub

val RsBlockExpr.isUnsafe: Boolean
    get() = (greenStub as? RsBlockExprStub)?.isUnsafe ?: (node.findChildByType(RsElementTypes.UNSAFE) != null)

val RsBlockExpr.isAsync: Boolean
    get() = (greenStub as? RsBlockExprStub)?.isAsync ?: (node.findChildByType(RsElementTypes.ASYNC) != null)

val RsBlockExpr.isTry: Boolean
    get() = (greenStub as? RsBlockExprStub)?.isTry ?: (node.findChildByType(RsElementTypes.TRY) != null)
