package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*

/**
 *  `RsExpr` related extensions
 */
// TODO: rename to make it clear that these are filed of the type and not of the expression.
val RsStructExpr.fields: List<RsNamedElement>
    get() = (path.reference.resolve() as? RsFieldsOwner)?.namedFields.orEmpty()


/**
 * Extracts [RsLitExpr] raw value
 */
val RsLitExpr.stringLiteralValue: String? get() = (kind as? RsTextLiteral)?.value
