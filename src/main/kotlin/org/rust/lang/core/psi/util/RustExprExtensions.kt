package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*

/**
 *  `RsExpr` related extensions
 */
// TODO: rename to make it clear that these are fields of the type and not of the expression.
val RsStructExpr.fields: List<RsNamedElement>
    get() = (path.reference.resolve() as? RsFieldsOwner)?.namedFields.orEmpty()


/**
 * Extracts [RsLitExpr] raw value
 */
val RsLitExpr.stringLiteralValue: String? get() = (kind as? RsTextLiteral)?.value

/**
 * Extracts the expression that defines the size of an array.
 */
val RsArrayExpr.sizeExpr: RsExpr?
    get() = if (semicolon != null && exprList.size == 2) exprList[1] else null
