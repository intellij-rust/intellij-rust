package org.rust.lang.core.names.parts

/**
 * Name-part of the anonymous 'identifier'
 */
object RustAnonymousIdPart : RustNamePart {
    override val identifier: String
        get() = "<anonymous>"
}
