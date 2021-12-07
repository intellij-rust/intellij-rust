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
    val typeSubst: Map<RsTypeParameter, TypeValue> = emptyMap(),
    val regionSubst: Map<RsLifetimeParameter, Value<RsLifetime>> = emptyMap(),
    val constSubst: Map<RsConstParameter, Value<RsElement>> = emptyMap(),
    val assoc: Map<RsTypeAlias, RsTypeReference> = emptyMap(),
) {
    sealed class TypeValue {
        object RequiredAbsent : TypeValue()
        object OptionalAbsent : TypeValue()
        sealed class Present : TypeValue() {
            class InAngles(val value: RsTypeReference) : Present()
            class FnSugar(val inputArgs: List<RsTypeReference?>) : Present()
        }
        class DefaultValue(val value: RsTypeReference, val selfTy: Ty?) : TypeValue()
    }

    sealed class Value<out T> {
        object RequiredAbsent : Value<Nothing>()
        object OptionalAbsent : Value<Nothing>()
        class Present<T>(val value: T) : Value<T>()
    }
}

fun RsPsiSubstitution.toSubst(resolver: PathExprResolver? = PathExprResolver.default): Substitution {
    val typeSubst = typeSubst.entries.associate { (param, value) ->
        val paramTy = TyTypeParameter.named(param)
        val valueTy = when (value) {
            is RsPsiSubstitution.TypeValue.DefaultValue -> if (value.selfTy != null) {
                value.value.type.substitute(mapOf(TyTypeParameter.self() to value.selfTy).toTypeSubst())
            } else {
                value.value.type
            }
            is RsPsiSubstitution.TypeValue.OptionalAbsent -> paramTy
            is RsPsiSubstitution.TypeValue.Present.InAngles -> value.value.type
            is RsPsiSubstitution.TypeValue.Present.FnSugar -> if (value.inputArgs.isNotEmpty()) {
                TyTuple(value.inputArgs.map { it?.type ?: TyUnknown })
            } else {
                TyUnit.INSTANCE
            }
            RsPsiSubstitution.TypeValue.RequiredAbsent -> TyUnknown
        }
        paramTy to valueTy
    }

    val regionSubst = regionSubst.entries.mapNotNull { (psiParam, psiValue) ->
        val param = ReEarlyBound(psiParam)
        val value = when (psiValue) {
            RsPsiSubstitution.Value.RequiredAbsent, RsPsiSubstitution.Value.OptionalAbsent -> return@mapNotNull null
            is RsPsiSubstitution.Value.Present -> psiValue.value.resolve()
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
                    is RsExpr -> value.evaluate(expectedTy, resolver)
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
        }

        param to value
    }

    return Substitution(typeSubst, regionSubst, constSubst)
}
