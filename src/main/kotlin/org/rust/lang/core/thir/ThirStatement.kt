/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.types.regions.Scope

// https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/thir.rs#L197
sealed class ThirStatement {
    abstract val destructionScope: Scope?

    data class Let(
        val remainderScope: Scope,
        val initScope: Scope,
        override val destructionScope: Scope?,
        val pattern: ThirPat,
        val initializer: ThirExpr?,
        val elseBlock: ThirBlock?,
    ) : ThirStatement()

    data class Expr(
        val scope: Scope,
        override val destructionScope: Scope?,
        val expr: ThirExpr,
    ) : ThirStatement()
}
