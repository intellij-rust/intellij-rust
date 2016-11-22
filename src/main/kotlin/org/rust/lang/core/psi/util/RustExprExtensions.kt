package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*

/**
 *  `RustExprElement` related extensions
 */
// TODO: rename to make it clear that these are filed of the type and not of the expression.
val RustStructExprElement.fields: List<RustNamedElement>
    get() = (path.reference.resolve() as? RustFieldsOwner)?.namedFields.orEmpty()


/**
 * Extracts [RustLitExprElement] raw value
 */
val RustLitExprElement.stringLiteralValue: String?
    get() = ((stringLiteral ?: rawStringLiteral) as? RustLiteral.Text)?.value
