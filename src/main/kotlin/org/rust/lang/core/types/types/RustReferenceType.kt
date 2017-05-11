package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

data class RustReferenceType(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustReferenceType && referenced.canUnifyWith(other.referenced, project)

    override fun toString(): String = "${if (mutable) "&mut " else "&"}$referenced"

    override fun substitute(map: Map<RustTypeParameterType, Ty>): Ty =
        RustReferenceType(referenced.substitute(map), mutable)
}
