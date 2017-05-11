package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters

interface TyStructOrEnumBase : Ty {
    val typeArguments: List<Ty>

    val typeArgumentsMapping: List<TyTypeParameter>

    val item: RsStructOrEnumItemElement

    override val typeParameterValues: Map<TyTypeParameter, Ty>
        get() = item.typeParameters.zip(typeArguments)
            .mapNotNull {
                val (param, arg) = it
                TyTypeParameter(param) to arg
            }.toMap()

    val fullName: String
        get() {
            return if (item.name != null) {
                item.name + if (typeArguments.isNotEmpty()) typeArguments.joinToString(", ", "<", ">") else ""
            } else "<anonymous>"
        }

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is TyStructOrEnumBase && item == other.item &&
            typeArguments.zip(other.typeArguments).all { (type1, type2) -> type1.canUnifyWith(type2, project) }

    fun aliasTypeArguments(typeArguments: List<TyTypeParameter>): Ty

    override fun withTypeArguments(typeArguments: List<Ty>): Ty =
        substitute(typeArgumentsMapping.withIndex().associate { (i, type) -> type to (typeArguments.getOrNull(i) ?: type) })
}

class TyStruct(
    struct: RsStructItem,
    override val typeArgumentsMapping: List<TyTypeParameter> = struct.typeParameters.map(::TyTypeParameter),
    override val typeArguments: List<Ty> = typeArgumentsMapping
) : TyStructOrEnumBase {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun toString(): String = fullName

    override fun withTypeArguments(typeArguments: List<Ty>): TyStruct =
        super.withTypeArguments(typeArguments) as TyStruct

    override fun aliasTypeArguments(typeArguments: List<TyTypeParameter>): TyStruct =
        TyStruct(item, typeArguments, this.typeArguments)

    override fun substitute(map: Map<TyTypeParameter, Ty>): TyStruct =
        TyStruct(item, typeArgumentsMapping, typeArguments.map { it.substitute(map) })

    override fun equals(other: Any?): Boolean =
        other is TyStruct && item == other.item && typeArguments == other.typeArguments

    override fun hashCode(): Int =
        item.hashCode() xor typeArguments.hashCode()
}

class TyEnum(
    enum: RsEnumItem,
    override val typeArgumentsMapping: List<TyTypeParameter> = enum.typeParameters.map(::TyTypeParameter),
    override val typeArguments: List<Ty> = typeArgumentsMapping
) : TyStructOrEnumBase {

    override val item = CompletionUtil.getOriginalOrSelf(enum)

    override fun toString(): String = fullName

    override fun withTypeArguments(typeArguments: List<Ty>): TyEnum =
        super.withTypeArguments(typeArguments) as TyEnum

    override fun aliasTypeArguments(typeArguments: List<TyTypeParameter>): TyEnum =
        TyEnum(item, typeArguments, this.typeArguments)

    override fun substitute(map: Map<TyTypeParameter, Ty>): TyEnum =
        TyEnum(item, typeArgumentsMapping, typeArguments.map { it.substitute(map) })

    override fun equals(other: Any?): Boolean =
        other is TyEnum && item == other.item && typeArguments == other.typeArguments

    override fun hashCode(): Int =
        item.hashCode() xor typeArguments.hashCode()
}
