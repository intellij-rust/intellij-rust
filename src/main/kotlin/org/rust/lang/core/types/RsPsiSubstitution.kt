/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.isConst
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.resolve
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.lang.utils.evaluation.evaluate

/** Similar to [Substitution], but maps PSI to PSI instead of [Ty] to [Ty] */
open class RsPsiSubstitution(
    val typeSubst: Map<RsTypeParameter, Value<TypeValue, TypeDefault>> = emptyMap(),
    val regionSubst: Map<RsLifetimeParameter, Value<RsLifetime, Nothing>> = emptyMap(),
    val constSubst: Map<RsConstParameter, Value<RsElement, RsExpr>> = emptyMap(),
    val assoc: Map<RsTypeAlias, RsTypeReference> = emptyMap(),
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
}

fun RsPsiSubstitution.toSubst(resolver: PathExprResolver? = PathExprResolver.default): Substitution {
    val typeSubst = typeSubst.entries.associate { (param, value) ->
        val paramTy = TyTypeParameter.named(param)
        val valueTy = when (value) {
            is RsPsiSubstitution.Value.DefaultValue -> if (value.value.selfTy != null) {
                value.value.value.type.substitute(mapOf(TyTypeParameter.self() to value.value.selfTy).toTypeSubst())
            } else {
                value.value.value.type
            }
            is RsPsiSubstitution.Value.OptionalAbsent -> paramTy
            is RsPsiSubstitution.Value.Present -> when (value.value) {
                is RsPsiSubstitution.TypeValue.InAngles -> value.value.value.type
                is RsPsiSubstitution.TypeValue.FnSugar -> if (value.value.inputArgs.isNotEmpty()) {
                    TyTuple(value.value.inputArgs.map { it?.type ?: TyUnknown })
                } else {
                    TyUnit.INSTANCE
                }
            }
            RsPsiSubstitution.Value.RequiredAbsent -> TyUnknown
        }
        paramTy to valueTy
    }

    val regionSubst = regionSubst.entries.mapNotNull { (psiParam, psiValue) ->
        val param = ReEarlyBound(psiParam)
        val value = if (psiValue is RsPsiSubstitution.Value.Present) {
            psiValue.value.resolve()
        } else {
            return@mapNotNull null
        }

        param to value
    }.toMap()

    val constSubst = constSubst.entries.associate { (psiParam, psiValue) ->
        val param = CtConstParameter(psiParam)
        val value = when (psiValue) {
            RsPsiSubstitution.Value.OptionalAbsent -> param
            RsPsiSubstitution.Value.RequiredAbsent -> CtUnknown
            is RsPsiSubstitution.Value.Present -> {
                val expectedTy = param.parameter.typeReference?.type ?: TyUnknown
                when (val value = psiValue.value) {
                    is RsExpr -> value.evaluate(expectedTy, resolver) // TODO check types
                    is RsBaseType -> when (val resolved = value.path?.reference?.resolve()) {
                        is RsConstParameter -> CtConstParameter(resolved)
                        is RsConstant -> when {
                            resolved.isConst -> {
                                // TODO check types
                                val type = resolved.typeReference?.type ?: TyUnknown
                                resolved.expr?.evaluate(type, resolver) ?: CtUnknown
                            }
                            else -> CtUnknown
                        }
                        else -> CtUnknown
                    }
                    else -> CtUnknown
                }
            }
            is RsPsiSubstitution.Value.DefaultValue -> {
                psiValue.value.evaluate(psiParam.typeReference?.type ?: TyUnknown)
            }
        }

        param to value
    }

    return Substitution(typeSubst, regionSubst, constSubst)
}
