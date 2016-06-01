package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustEnumVariant
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructExpr
import org.rust.lang.core.psi.RustStructItem

/**
 *  `RustExpr` related extensions
 */

fun RustStructExpr.visibleFields(): List<RustNamedElement> {
    val structOrEnum = path.reference.resolve() ?: return emptyList()
    return when (structOrEnum) {
        is RustStructItem -> structOrEnum.structDeclArgs?.fieldDeclList
        is RustEnumVariant -> structOrEnum.enumStructArgs?.fieldDeclList
        else               -> null
    }.orEmpty()
}

