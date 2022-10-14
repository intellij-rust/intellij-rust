/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.ty.Ty

/** Similar to [Substitution], but maps PSI to PSI instead of [Ty] to [Ty] */
open class RsPsiSubstitution(
    val typeSubst: Map<RsTypeParameter, Value<TypeValue, TypeDefault>> = emptyMap(),
    val regionSubst: Map<RsLifetimeParameter, Value<RsLifetime, Nothing>> = emptyMap(),
    val constSubst: Map<RsConstParameter, Value<RsElement, RsExpr>> = emptyMap(),
    val assoc: Map<RsTypeAlias, AssocValue> = emptyMap(),
) {
    sealed class Value<out P, out D> {
        object RequiredAbsent : Value<Nothing, Nothing>()
        object OptionalAbsent : Value<Nothing, Nothing>()
        class Present<P>(val value: P) : Value<P, Nothing>()
        class DefaultValue<D>(val value: D) : Value<Nothing, D>()
    }

    sealed class TypeValue {
        class InAngles(val value: RsTypeReference) : TypeValue()
        class FnSugar(val inputArgs: List<RsTypeReference?>) : TypeValue()
    }
    data class TypeDefault(val value: RsTypeReference, val selfTy: Ty?)

    sealed class AssocValue {
        class Present(val value: RsTypeReference) : AssocValue()
        object FnSugarImplicitRet : AssocValue()
    }
}
