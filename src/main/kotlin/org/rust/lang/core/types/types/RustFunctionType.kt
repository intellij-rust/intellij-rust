package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

data class RustFunctionType(val paramTypes: List<Ty>, val retType: Ty) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustFunctionType && paramTypes.size == other.paramTypes.size &&
            paramTypes.zip(other.paramTypes).all { (type1, type2) -> type1.canUnifyWith(type2, project) } &&
            retType.canUnifyWith(other.retType, project)

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === RustUnitType) params else "$params -> $retType"
    }

    override fun substitute(map: Map<RustTypeParameterType, Ty>): RustFunctionType =
        RustFunctionType(paramTypes.map { it.substitute(map) }, retType.substitute(map))
}
