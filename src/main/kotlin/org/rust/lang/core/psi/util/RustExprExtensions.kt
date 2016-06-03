package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustEnumVariantElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructExprElement
import org.rust.lang.core.psi.RustStructItemElement

/**
 *  `RustExprElement` related extensions
 */

val RustStructExprElement.visibleFields: List<RustNamedElement> get() {
    val structOrEnum = path.reference.resolve() ?: return emptyList()
    return when (structOrEnum) {
        is RustStructItemElement  -> structOrEnum.structDeclArgs?.fieldDeclList
        is RustEnumVariantElement -> structOrEnum.enumStructArgs?.fieldDeclList
        else -> null
    }.orEmpty()
}

