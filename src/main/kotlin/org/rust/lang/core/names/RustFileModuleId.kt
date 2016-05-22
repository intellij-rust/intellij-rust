package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustFileModuleIdPart
import org.rust.lang.core.resolve.indexes.RustCratePath

class RustFileModuleId(val path: RustCratePath)
    : RustQualifiedNameBase<RustFileModuleIdPart>(RustFileModuleIdPart(path)) {

    override fun toString(): String = "<$path>"

}
