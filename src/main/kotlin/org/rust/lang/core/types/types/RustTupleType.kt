package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

data class RustTupleType(val types: List<RustType>) : RustType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustTupleType && types.size == other.types.size &&
            types.zip(other.types).all { (type1, type2) -> type1.canUnifyWith(type2, project) }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustTupleType =
        RustTupleType(types.map { it.substitute(map) })

    override fun toString(): String = types.joinToString(", ", "(", ")")
}

