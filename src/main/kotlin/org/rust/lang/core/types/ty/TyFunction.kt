package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyFunction(val paramTypes: List<Ty>, val retType: Ty) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyFunction && paramTypes.size == other.paramTypes.size &&
            paramTypes.zip(other.paramTypes).all { (type1, type2) -> type1.canUnifyWith(type2, project) } &&
            retType.canUnifyWith(other.retType, project)

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === TyUnit) params else "$params -> $retType"
    }

    override fun substitute(map: Map<TyTypeParameter, Ty>): TyFunction =
        TyFunction(paramTypes.map { it.substitute(map) }, retType.substitute(map))
}
