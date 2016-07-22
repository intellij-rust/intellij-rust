package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustFieldsOwner
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructExprElement
import org.rust.lang.core.psi.fields

/**
 *  `RustExprElement` related extensions
 */
// TODO: rename to make it clear that these are filed of the type and not of the expression.
val RustStructExprElement.fields: List<RustNamedElement> get() =
    (path.reference.resolve() as? RustFieldsOwner)?.fields.orEmpty()

