package org.rust.lang.core.names.parts

import org.rust.lang.core.resolve.indexes.RustCratePath

class RustFileModuleIdPart(val path: RustCratePath) : RustNamePart {
    override val identifier: String
        get() = "<module: $path>"
}
