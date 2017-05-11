package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

data class RustSliceType(val elementType: Ty) : Ty {
    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustSliceType && elementType.canUnifyWith(other.elementType, project)

    override fun substitute(map: Map<RustTypeParameterType, Ty>): Ty {
        return RustSliceType(elementType.substitute(map))
    }

    override fun toString() = "[$elementType]"
}
