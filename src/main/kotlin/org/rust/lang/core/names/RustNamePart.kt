package org.rust.lang.core.names

interface RustNamePart {

    /**
     * Required `name` part of this particular identifier-part
     */
    val identifier: String


    companion object {
        fun parse(s: String): RustNamePart {
            return RustIdNamePart(s)
        }
    }
}
