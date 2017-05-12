package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project


class TyArray(val base: Ty, val size: Int) : Ty {
    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyArray && size == other.size && base.canUnifyWith(other.base, project)

    override fun substitute(map: TypeArguments): Ty =
        TyArray(base.substitute(map), size)

    override fun toString() = "[$base; $size]"
}
