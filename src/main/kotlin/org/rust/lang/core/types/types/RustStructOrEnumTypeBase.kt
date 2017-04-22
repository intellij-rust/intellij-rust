package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.RustType

interface RustStructOrEnumTypeBase : RustType {
    val typeArguments: List<RustType>

    val typeArgumentsMapping: List<RustTypeParameterType>

    val item: RsStructOrEnumItemElement

    override val typeParameterValues: Map<RustTypeParameterType, RustType>
        get() = item.typeParameters.zip(typeArguments)
            .mapNotNull {
                val (param, arg) = it
                RustTypeParameterType(param) to arg
            }.toMap()

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustStructOrEnumTypeBase && item == other.item &&
            typeArguments.zip(other.typeArguments).all { (type1, type2) -> type1.canUnifyWith(type2, project)}

    fun aliasTypeArguments(typeArguments: List<RustTypeParameterType>): RustType

    override fun withTypeArguments(typeArguments: List<RustType>): RustType =
        substitute(typeArgumentsMapping.withIndex().associate { (i, type) -> type to (typeArguments.getOrNull(i) ?: type) })
}
