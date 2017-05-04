package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

data class RustReferenceType(val referenced: RustType, val mutable: Boolean = false) : RustType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustReferenceType && referenced.canUnifyWith(other.referenced, project)


    override fun toString(): String = "${if (mutable) "&mut" else "&"} $referenced"

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType =
        RustReferenceType(referenced.substitute(map), mutable)
}
