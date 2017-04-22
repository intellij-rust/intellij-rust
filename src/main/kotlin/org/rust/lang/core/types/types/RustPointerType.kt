package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

data class RustPointerType(val referenced: RustType, val mutable: Boolean = false) : RustType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustPointerType && referenced.canUnifyWith(other.referenced, project)

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType =
        RustPointerType(referenced.substitute(map), mutable)
}
