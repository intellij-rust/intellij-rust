package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

data class RustSliceType(val elementType: RustType) : RustPrimitiveType {
    override fun toString() = "[$elementType]"

    override fun canUnifyWith(other: RustType, project: Project): Boolean {
        return other is RustSliceType && elementType.canUnifyWith(other.elementType, project)
    }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType {
        return RustSliceType(elementType.substitute(map))
    }
}
