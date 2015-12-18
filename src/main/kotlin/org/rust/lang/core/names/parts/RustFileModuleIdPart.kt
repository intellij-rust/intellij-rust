package org.rust.lang.core.names.parts

import org.rust.lang.core.resolve.indexes.RustModulePath

class RustFileModuleIdPart(val path: RustModulePath) : RustNamePart {
    override val identifier: String
        get() = "<module: $path>"
}
