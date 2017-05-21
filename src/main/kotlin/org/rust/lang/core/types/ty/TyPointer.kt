package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyPointer(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyPointer && referenced.canUnifyWith(other.referenced, project)

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"

    override fun substitute(map: TypeArguments): Ty =
        TyPointer(referenced.substitute(map), mutable)
}
