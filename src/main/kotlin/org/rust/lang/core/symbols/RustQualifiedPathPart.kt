package org.rust.lang.core.symbols

import org.rust.lang.core.symbols.impl.*

interface RustQualifiedPathPart {

    companion object {
        fun from(name: String?): RustQualifiedPathPart =
            when (name) {
                "super" -> RustSuperQualifiedPathPart
                "self"  -> RustSelfQualifiedPathPart
                "Self"  -> RustCSelfQualifiedPathPart
                null    -> RustAnonymousQualifiedPathPart
                else    -> RustNamedQualifiedPathPart(name)
            }
    }

    val name: String

    /*
    val genericArgs: ?
    */

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}
