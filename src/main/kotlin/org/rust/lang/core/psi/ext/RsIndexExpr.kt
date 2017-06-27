/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIndexExpr

val RsIndexExpr.containerExpr: RsExpr?
    get() = exprList.getOrNull(0)

val RsIndexExpr.indexExpr: RsExpr?
    get() = exprList.getOrNull(1)
