package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyReference(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyReference && referenced.canUnifyWith(other.referenced, project)

    override fun toString(): String = "${if (mutable) "&mut " else "&"}$referenced"

    override fun substitute(map: Map<TyTypeParameter, Ty>): Ty =
        TyReference(referenced.substitute(map), mutable)
}
