/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter

interface RsGenericDeclaration : RsElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

fun RsGenericDeclaration.getGenericParameters(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true
): List<RsGenericParameter> = typeParameterList?.getGenericParameters(
    includeLifetimes,
    includeTypes,
    includeConsts
).orEmpty()

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()

val RsGenericDeclaration.lifetimeParameters: List<RsLifetimeParameter>
    get() = typeParameterList?.lifetimeParameterList.orEmpty()

val RsGenericDeclaration.constParameters: List<RsConstParameter>
    get() = typeParameterList?.constParameterList.orEmpty()

val RsGenericDeclaration.requiredGenericParameters: List<RsGenericParameter>
    get() = getGenericParameters().filter {
        when (it) {
            is RsTypeParameter -> it.typeReference == null
            is RsConstParameter -> it.expr == null
            else -> false
        }
    }

fun <T : RsGenericDeclaration> T.withSubst(vararg subst: Ty): BoundElement<T> {
    val typeParameterList = typeParameters
    val nonDefaultCount = typeParameterList.asSequence()
        .takeWhile { it.typeReference == null }
        .count()
    val substitution = if (subst.size < nonDefaultCount || subst.size > typeParameterList.size) {
        val name = if (this is RsNamedElement) name else "unnamed"
        LOG.warn("Item `$name` has ${typeParameterList.size} type parameters but received ${subst.size} types for substitution")
        emptySubstitution
    } else {
        typeParameterList.withIndex().associate { (i, par) ->
            val paramTy = TyTypeParameter.named(par)
            paramTy to (subst.getOrNull(i) ?: par.typeReference?.rawType ?: paramTy)
        }.toTypeSubst()
    }
    return BoundElement(this, substitution)
}

fun <T : RsGenericDeclaration> T.withDefaultSubst(): BoundElement<T> =
    BoundElement(this, defaultSubstitution(this))

private fun <T : RsGenericDeclaration> defaultSubstitution(item: T): Substitution {
    val typeSubst = item.typeParameters.associate {
        val parameter = TyTypeParameter.named(it)
        parameter to parameter
    }
    val regionSubst = item.lifetimeParameters.associate {
        val parameter = ReEarlyBound(it)
        parameter to parameter
    }
    val constSubst = item.constParameters.associate {
        val parameter = CtConstParameter(it)
        parameter to parameter
    }
    return Substitution(typeSubst, regionSubst, constSubst)
}

private val LOG: Logger = logger<RsGenericDeclaration>()
