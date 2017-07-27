/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.resolve.ImplLookup

typealias Substitution = Map<TyTypeParameter, Ty>
typealias TypeMapping = MutableMap<TyTypeParameter, Ty>
val emptySubstitution: Substitution = emptyMap()

/**
 * Represents both a type, like `i32` or `S<Foo, Bar>`, as well
 * as an unbound constructor `S`.
 *
 * The name `Ty` is short for `Type`, inspired by the Rust
 * compiler.
 */
interface Ty {
    /**
     * Checks if `other` type may be represented as this type.
     *
     * Note that `t1.canUnifyWith(t2)` is not the same as `t2.canUnifyWith(t1)`.
     */
    fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping? = null): Boolean

    /**
     * Substitute type parameters for their values
     *
     * This works for `struct S<T> { field: T }`, when we
     * know the type of `T` and want to find the type of `field`.
     */
    fun substitute(subst: Substitution): Ty = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    val typeParameterValues: Substitution get() = emptySubstitution

    /**
     * User visible string representation of a type
     */
    override fun toString(): String
}

fun Ty.getTypeParameter(name: String): TyTypeParameter? {
    return typeParameterValues.keys.find { it.toString() == name }
}

internal inline fun merge(mapping: TypeMapping?, canUnify: (TypeMapping?) -> Boolean): Boolean {
    return if (mapping != null) {
        val innerMapping = mutableMapOf<TyTypeParameter, Ty>()
        val result = canUnify(innerMapping)
        if (result) {
            mapping.merge(innerMapping)
        }
        result
    } else {
        canUnify(null)
    }
}

internal fun TypeMapping.merge(otherMapping: Substitution) {
    for ((param, value) in otherMapping) {
        val old = get(param)
        if (old == null || old == TyUnknown || old is TyNumeric && old.isKindWeak) {
            put(param, value)
        }
    }
}

fun Substitution.substituteInValues(map: Substitution): Substitution =
    mapValues { (_, value) -> value.substitute(map) }

fun Substitution.get(psi: RsTypeParameter): Ty? {
    return get(TyTypeParameter.named((psi)))
}
