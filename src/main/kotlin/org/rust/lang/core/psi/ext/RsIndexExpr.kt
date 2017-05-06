package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIndexExpr

val RsIndexExpr.containerExpr: RsExpr?
    get() = exprList.getOrNull(0)

val RsIndexExpr.indexExpr: RsExpr?
    get() = exprList.getOrNull(1)
