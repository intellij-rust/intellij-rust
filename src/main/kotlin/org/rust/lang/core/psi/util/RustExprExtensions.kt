package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*

/**
 *  `RsExpr` related extensions
 */
// TODO: rename to make it clear that these are filed of the type and not of the expression.
val RsStructExpr.fields: List<RustNamedElement>
    get() = (path.reference.resolve() as? RustFieldsOwner)?.namedFields.orEmpty()


/**
 * Extracts [RsLitExpr] raw value
 */
val RsLitExpr.stringLiteralValue: String?
    get() = ((stringLiteral ?: rawStringLiteral) as? RustLiteral.Text)?.value
