package org.rust.lang.core.names.parts

/**
 * Part of the URI (represented as fully-qualified name) corresponding
 * to particular item, bearing no more info beyond the name of this item
 */
interface RustNamePart {

    /**
     * Required `name` part of this particular identifier-part
     */
    val identifier: String

}
