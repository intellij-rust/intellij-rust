/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.types.regions.Scope

sealed class ThirStatement {
    data class Let(
        val remainderScope: Scope,
        val initScope: Scope,
        val pattern: ThirPat,
        val initializer: ThirExpr?,
        val elseBlock: ThirBlock?,
    ) : ThirStatement()

    data class Expr(
        val scope: Scope,
        val expr: ThirExpr,
    ) : ThirStatement()
}
