/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown

/**
 * An element that can be called using [RsCallExpr].
 */
sealed interface RsCallable {
    val selfParameter: RsSelfParameter?
    val parameterTypes: List<Ty>
    val rawReturnType: Ty
    val isAsync: Boolean
    val knownItems: KnownItems
    val isActuallyUnsafe: Boolean
    val owner: RsAbstractableOwner
    val isVariadic: Boolean
    val name: String?
    val isIntrinsic: Boolean
    val queryAttributes: QueryAttributes<RsMetaItem>


    /**
     * ```rust
     * fn foo() {
     * }
     *
     * fn main() {
     *     let a = foo;
     *     a();
     * }
     * ```
     */
    data class Function(val fn: RsFunction) : RsCallable {
        override val name get() = fn.name
        override val selfParameter get() = fn.selfParameter
        override val parameterTypes get() = fn.valueParameters.map { it.typeReference?.rawType ?: TyUnknown }
        override val rawReturnType get() = fn.rawReturnType
        override val isAsync get() = fn.isAsync
        override val knownItems get() = fn.knownItems
        override val isActuallyUnsafe get() = fn.isActuallyUnsafe
        override val owner get() = fn.owner
        override val isVariadic get() = fn.isVariadic
        override val isIntrinsic get() = fn.isIntrinsic
        override val queryAttributes get() = fn.queryAttributes
    }


    /**
     * ```rust
     * enum A {
     *     Y(i32);
     * }
     *
     * fn main() {
     *     let y = A::Y;
     *     y(1);
     * }
     * ```
     */
    data class EnumVariant(val enumVariant: RsEnumVariant) : RsCallable {
        override val name get() = enumVariant.name
        override val selfParameter get() = null
        override val parameterTypes get() = enumVariant.fieldTypes
        override val rawReturnType get() = enumVariant.parentEnum.declaredType
        override val isAsync get() = false
        override val knownItems get() = enumVariant.knownItems
        override val isActuallyUnsafe get() = false
        override val owner get() = RsAbstractableOwner.Free
        override val isVariadic get() = false
        override val isIntrinsic get() = false
        override val queryAttributes get() = enumVariant.queryAttributes
    }

    /**
     * ```rust
     * struct S(i32);
     *
     * fn main() {
     *     let s = S;
     *     s(1);
     * }
     * ```
     */
    data class StructItem(val structItem: RsStructItem) : RsCallable {
        override val selfParameter get() = null
        override val parameterTypes get() = structItem.fieldTypes
        override val rawReturnType get() = structItem.declaredType
        override val isAsync get() = false
        override val knownItems get() = structItem.knownItems
        override val isActuallyUnsafe get() = false
        override val owner get() = RsAbstractableOwner.Free
        override val isVariadic get() = false
        override val name get() = structItem.name
        override val isIntrinsic get() = false
        override val queryAttributes get() = structItem.queryAttributes
    }
}

