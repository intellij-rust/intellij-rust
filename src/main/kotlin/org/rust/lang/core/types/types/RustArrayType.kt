package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType


class RustArrayType(val base: RustType, val size: Int) : RustPrimitiveType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustArrayType && size == other.size && base.canUnifyWith(other.base, project)

    override fun toString() = "[$base; $size]"

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType =
        RustArrayType(base.substitute(map), size)
}
