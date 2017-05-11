package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

data class RustPointerType(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustPointerType && referenced.canUnifyWith(other.referenced, project)

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"

    override fun substitute(map: Map<RustTypeParameterType, Ty>): Ty =
        RustPointerType(referenced.substitute(map), mutable)
}
