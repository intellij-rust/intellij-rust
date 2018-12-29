/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsElementTypes

val RsBlockExpr.isUnsafe: Boolean
    get() = node.findChildByType(RsElementTypes.UNSAFE) != null

val RsBlockExpr.isAsync: Boolean
    get() = node.findChildByType(RsElementTypes.ASYNC) != null

val RsBlockExpr.isTry: Boolean
    get() = node.findChildByType(RsElementTypes.TRY) != null
