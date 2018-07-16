/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.stdext.zipValues

open class Substitution(
    val typeSubst: Map<TyTypeParameter, Ty> = emptyMap(),
    val lifetimeSubst: Map<ReEarlyBound, Region> = emptyMap()
) {
    val types: Collection<Ty> get() = typeSubst.values
    val lifetimes: Collection<Region> get() = lifetimeSubst.values
    val kinds: Collection<Kind> get() = types + lifetimes

    operator fun plus(other: Substitution): Substitution =
        Substitution(mergeMaps(typeSubst, other.typeSubst), mergeMaps(lifetimeSubst, other.lifetimeSubst))

    operator fun get(key: TyTypeParameter) = typeSubst[key]
    operator fun get(key: ReEarlyBound) = lifetimeSubst[key]
    operator fun get(psi: RsTypeParameter) = typeSubst[TyTypeParameter.named((psi))]
    operator fun get(psi: RsLifetimeParameter) = lifetimeSubst[ReEarlyBound((psi))]

    fun typeByName(name: String): Ty =
        typeSubst.entries.find { it.key.toString() == name }?.value ?: TyUnknown

    fun typeParameterByName(name: String): TyTypeParameter? =
        typeSubst.keys.find { it.toString() == name }

    fun substituteInValues(map: Substitution): Substitution =
        Substitution(
            typeSubst.mapValues { (_, value) -> value.substitute(map) },
            lifetimeSubst.mapValues { (_, value) -> value.substitute(map) }
        )

    fun foldValues(folder: TypeFolder): Substitution =
        Substitution(
            typeSubst.mapValues { (_, value) -> value.foldWith(folder) },
            lifetimeSubst.mapValues { (_, value) -> value.foldWith(folder) }
        )

    fun zipTypeValues(other: Substitution): List<Pair<Ty, Ty>> = zipValues(typeSubst, other.typeSubst)

    fun mapTypeValues(transform: (Map.Entry<TyTypeParameter, Ty>) -> Ty): Substitution =
        Substitution(typeSubst.mapValues(transform), lifetimeSubst)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        other !is Substitution -> false
        typeSubst != other.typeSubst -> false
        else -> true
    }

    override fun hashCode(): Int = typeSubst.hashCode()
}

private object EmptySubstitution : Substitution()

val emptySubstitution: Substitution = EmptySubstitution

fun Map<TyTypeParameter, Ty>.toTypeSubst() = Substitution(typeSubst = this)

private fun <K, V> mergeMaps(map1: Map<K, V>, map2: Map<K, V>): Map<K, V> =
    if (map1.isEmpty() && map2.isEmpty()) emptyMap() else HashMap(map1).apply { putAll(map2) }
