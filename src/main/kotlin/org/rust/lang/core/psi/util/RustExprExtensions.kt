package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustEnumVariantElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructExprElement
import org.rust.lang.core.psi.RustStructItemElement

/**
 *  `RustExprElement` related extensions
 */
// TODO: rename to make it clear that these are filed of the type and not of the expression.
val RustStructExprElement.fields: List<RustNamedElement> get() {
    val structOrEnum = path.reference.resolve() ?: return emptyList()
    return when (structOrEnum) {
        is RustStructItemElement  -> structOrEnum.fields
        is RustEnumVariantElement -> structOrEnum.blockFields?.fieldDeclList ?: emptyList()
        else -> emptyList()
    }
}

