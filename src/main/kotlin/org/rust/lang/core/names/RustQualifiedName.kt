package org.rust.lang.core.names

/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * @name        Non-qualified name-part
 * @qualifier   Qualified name-part
 */
data class RustQualifiedName(val part: RustNamePart, val qualifier: RustQualifiedName? = null) {
    companion object {
        fun parse(s: String): RustQualifiedName? {
            val q: RustQualifiedName? = null
            return s.split("::").fold(q, {
                qual, part -> RustQualifiedName(RustNamePart.parse(part), qual)
            })
        }

        fun toString(qualName: RustQualifiedName): String {
            val b = StringBuilder()

            val quals = arrayListOf<RustQualifiedName>()

            var qual: RustQualifiedName? = qualName
            while (qual != null) {
                quals.add(qual)
                qual = qual.qualifier
            }

            quals.asReversed().forEach { q ->
                val b = StringBuilder()

                b.append(q)
                b.append("::")
            }

            return b.toString()
        }
    }
}
