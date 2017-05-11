package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.Ty

interface RustStructOrEnumTypeBase : Ty {
    val typeArguments: List<Ty>

    val typeArgumentsMapping: List<RustTypeParameterType>

    val item: RsStructOrEnumItemElement

    override val typeParameterValues: Map<RustTypeParameterType, Ty>
        get() = item.typeParameters.zip(typeArguments)
            .mapNotNull {
                val (param, arg) = it
                RustTypeParameterType(param) to arg
            }.toMap()

    val fullName: String
        get() {
            return if (item.name != null) {
                item.name + if (typeArguments.isNotEmpty()) typeArguments.joinToString(", ", "<", ">") else ""
            } else "<anonymous>"
        }

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustStructOrEnumTypeBase && item == other.item &&
            typeArguments.zip(other.typeArguments).all { (type1, type2) -> type1.canUnifyWith(type2, project)}

    fun aliasTypeArguments(typeArguments: List<RustTypeParameterType>): Ty

    override fun withTypeArguments(typeArguments: List<Ty>): Ty =
        substitute(typeArgumentsMapping.withIndex().associate { (i, type) -> type to (typeArguments.getOrNull(i) ?: type) })
}
