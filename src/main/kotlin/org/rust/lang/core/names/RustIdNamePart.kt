package org.rust.lang.core.names

/**
 * Name-part consisting of the sole part -- identifier
 */
class RustIdNamePart(override val identifier: String) : RustNamePart {
    override fun toString(): String = identifier
}

