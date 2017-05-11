package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty


class RustArrayType(val base: Ty, val size: Int) : RustPrimitiveType {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustArrayType && size == other.size && base.canUnifyWith(other.base, project)

    override fun toString() = "[$base; $size]"

    override fun substitute(map: Map<RustTypeParameterType, Ty>): Ty =
        RustArrayType(base.substitute(map), size)
}
