/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayExpr
import org.rust.lang.core.psi.RsExpr

/**
 * Extracts the expression that defines the array initializer.
 */
val RsArrayExpr.initializer: RsExpr?
    get() = if (semicolon != null && exprList.size == 2) exprList[0] else null

/**
 * Extracts the expression that defines the size of an array.
 */
val RsArrayExpr.sizeExpr: RsExpr?
    get() = if (semicolon != null && exprList.size == 2) exprList[1] else null

/**
 * Extracts the expression list that defines the elements of an array.
 */
val RsArrayExpr.arrayElements: List<RsExpr>?
    get() = if (semicolon == null) exprList else null
