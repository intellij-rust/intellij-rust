/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.type

interface TyStructOrEnumBase : Ty {
    val typeArguments: List<Ty>

    val item: RsStructOrEnumItemElement

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return if (other is TyStructOrEnumBase && item == other.item) {
            UnifyResult.mergeAll(typeArguments.zip(other.typeArguments).map { (type1, type2) -> type1.unifyWith(type2, lookup) })
        } else {
            UnifyResult.fail
        }
    }
}

class TyStruct private constructor(
    private val boundElement: BoundElement<RsStructItem>
) : TyStructOrEnumBase {

    override val item: RsStructItem
        get() = boundElement.element

    override val typeParameterValues: Substitution
        get() = boundElement.subst

    override val typeArguments: List<Ty>
        get() = item.typeParameters.map { typeParameterValues.get(it) ?: TyUnknown }

    override fun superFoldWith(folder: TypeFolder): TyStruct =
        TyStruct(boundElement.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        boundElement.visitWith(visitor)

    override fun equals(other: Any?): Boolean =
        other is TyStruct && boundElement == other.boundElement

    override fun hashCode(): Int =
        boundElement.hashCode()

    override fun toString(): String = tyToString(this)

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

    override val typeParameterValues: Substitution
        get() = boundElement.subst

    override val typeArguments: List<Ty>
        get() = item.typeParameters.map { typeParameterValues.get(it) ?: TyUnknown }

    override fun superFoldWith(folder: TypeFolder): TyEnum =
        TyEnum(boundElement.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        boundElement.visitWith(visitor)

    override fun equals(other: Any?): Boolean =
        other is TyEnum && boundElement == other.boundElement

    override fun hashCode(): Int =
        boundElement.hashCode()

    override fun toString(): String = tyToString(this)

    companion object {
        fun valueOf(enum: RsEnumItem): TyEnum {
            val item = CompletionUtil.getOriginalOrSelf(enum)
            return TyEnum(BoundElement(item, defaultSubstitution(enum)))
        }
    }
}

private fun defaultSubstitution(item: RsStructOrEnumItemElement): Substitution =
    item.typeParameters.associate { rsTypeParameter ->
        val tyTypeParameter = TyTypeParameter.named(rsTypeParameter)
        val defaultType = rsTypeParameter.typeReference?.type ?: tyTypeParameter
        tyTypeParameter to defaultType
    }
