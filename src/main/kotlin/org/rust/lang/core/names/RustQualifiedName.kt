package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.names.parts.RustNamePart

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * Serves primarily as an URI for items inside the Rust's crates
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
open class RustQualifiedName(val part: RustNamePart, val qualifier: RustQualifiedName? = null) {

    override fun toString(): String =
        "${qualifier?.toString()}::${part.toString()}"

    companion object {

        fun parse(s: String): RustQualifiedName? {
            return s.split("::").fold(RustAnonymousId as RustQualifiedName, {
                qual, part -> RustQualifiedName(RustIdNamePart.parse(part), qual)
            })
        }

    }

}
