/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.substitute

typealias Substitution = Map<TyTypeParameter, Ty>
val emptySubstitution: Substitution = emptyMap()

/**
 * Represents both a type, like `i32` or `S<Foo, Bar>`, as well
 * as an unbound constructor `S`.
 *
 * The name `Ty` is short for `Type`, inspired by the Rust
 * compiler.
 */
interface Ty: TypeFoldable<Ty> {
    /**
     * Checks if `other` type may be represented as this type.
     *
     * Note that `t1.unifyWith(t2)` is not the same as `t2.unifyWith(t1)`.
     *
     * TODO replace it with the truly unification
     */
    fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult

    override fun foldWith(folder: TypeFolder): Ty = folder(this)

    override fun superFoldWith(folder: TypeFolder): Ty = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    val typeParameterValues: Substitution get() = emptySubstitution

    /**
     * User visible string representation of a type
     */
    override fun toString(): String
}

enum class Mutability {
    MUTABLE,
    IMMUTABLE;

    val isMut: Boolean get() = this == MUTABLE

    companion object {
        fun valueOf(mutable: Boolean): Mutability =
            if (mutable) MUTABLE else IMMUTABLE
    }
}

fun Ty.getTypeParameter(name: String): TyTypeParameter? {
    return typeParameterValues.keys.find { it.toString() == name }
}

fun Substitution.substituteInValues(map: Substitution): Substitution =
    mapValues { (_, value) -> value.substitute(map) }

fun Substitution.foldValues(folder: TypeFolder): Substitution =
    mapValues { (_, value) -> value.foldWith(folder) }

fun Substitution.get(psi: RsTypeParameter): Ty? {
    return get(TyTypeParameter.named((psi)))
}
