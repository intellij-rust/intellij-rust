package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

data class RustTupleType(val types: List<Ty>) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustTupleType && types.size == other.types.size &&
            types.zip(other.types).all { (type1, type2) -> type1.canUnifyWith(type2, project) }

    override fun substitute(map: Map<RustTypeParameterType, Ty>): RustTupleType =
        RustTupleType(types.map { it.substitute(map) })

    override fun toString(): String = types.joinToString(", ", "(", ")")
}

