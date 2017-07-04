/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.type

interface TyStructOrEnumBase : Ty {
    val typeArguments: List<Ty>

    val item: RsStructOrEnumItemElement

    val fullName: String
        get() {
            return if (item.name != null) {
                item.name + if (typeArguments.isNotEmpty()) typeArguments.joinToString(", ", "<", ">") else ""
            } else "<anonymous>"
        }

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyStructOrEnumBase && item == other.item &&
            typeArguments.zip(other.typeArguments).all { (type1, type2) -> type1.canUnifyWith(type2, project, it) }
    }
}

class TyStruct private constructor(
    private val boundElement: BoundElement<RsStructItem>
) : TyStructOrEnumBase {

    override val item: RsStructItem
        get() = boundElement.element

    override val typeParameterValues: TypeArguments
        get() = boundElement.typeArguments

    override val typeArguments: List<Ty>
        get() = item.typeParameters.map { typeParameterValues[TyTypeParameter(it)] ?: TyUnknown }

    override fun toString(): String = fullName

    override fun substitute(map: TypeArguments): TyStruct =
        TyStruct(BoundElement(boundElement.element, boundElement.typeArguments.substituteInValues(map)))

    override fun equals(other: Any?): Boolean =
        other is TyStruct && boundElement == other.boundElement

    override fun hashCode(): Int =
        boundElement.hashCode()

    companion object {
        fun valueOf(struct: RsStructItem): TyStruct {
            val item = CompletionUtil.getOriginalOrSelf(struct)
            return TyStruct(BoundElement(item, defaultSubstitution(struct)))
        }
    }
}

class TyEnum private constructor(
    private val boundElement: BoundElement<RsEnumItem>
) : TyStructOrEnumBase {

    override val item: RsEnumItem
        get() = boundElement.element

    override val typeParameterValues: TypeArguments
        get() = boundElement.typeArguments

    override val typeArguments: List<Ty>
        get() = item.typeParameters.map { typeParameterValues[TyTypeParameter(it)] ?: TyUnknown }

    override fun toString(): String = fullName

    override fun substitute(map: TypeArguments): TyEnum =
        TyEnum(BoundElement(boundElement.element, boundElement.typeArguments.substituteInValues(map)))

    override fun equals(other: Any?): Boolean =
        other is TyEnum && boundElement == other.boundElement

    override fun hashCode(): Int =
        boundElement.hashCode()

    companion object {
        fun valueOf(enum: RsEnumItem): TyEnum {
            val item = CompletionUtil.getOriginalOrSelf(enum)
            return TyEnum(BoundElement(item, defaultSubstitution(enum)))
        }
    }
}

private fun defaultSubstitution(item: RsStructOrEnumItemElement): TypeArguments =
    item.typeParameters.associate { rsTypeParameter ->
        val tyTypeParameter = TyTypeParameter(rsTypeParameter)
        val defaultType = rsTypeParameter.typeReference?.type ?: tyTypeParameter
        tyTypeParameter to defaultType
    }
