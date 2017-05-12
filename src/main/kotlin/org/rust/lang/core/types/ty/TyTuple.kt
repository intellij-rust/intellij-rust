package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyTuple(val types: List<Ty>) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyTuple && types.size == other.types.size &&
            types.zip(other.types).all { (type1, type2) -> type1.canUnifyWith(type2, project) }

    override fun substitute(map: TypeArguments): TyTuple =
        TyTuple(types.map { it.substitute(map) })

    override fun toString(): String = types.joinToString(", ", "(", ")")
}

