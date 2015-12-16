package org.rust.lang.core.names.parts

/**
 * Name-part consisting of the sole part -- identifier
 */
data class RustIdNamePart(override val identifier: String) : RustNamePart {
    override fun toString(): String = identifier

    companion object {
        fun parse(s: String): RustNamePart {
            return RustIdNamePart(s)
        }
    }
}

