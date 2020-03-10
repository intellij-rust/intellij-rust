/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
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
    val regionSubst: Map<ReEarlyBound, Region> = emptyMap(),
    val constSubst: Map<CtConstParameter, Const> = emptyMap()
) {
    val types: Collection<Ty> get() = typeSubst.values
    val regions: Collection<Region> get() = regionSubst.values
    val consts: Collection<Const> get() = constSubst.values
    val kinds: Collection<Kind> get() = (types as Collection<Kind>) + regions + consts

    operator fun plus(other: Substitution): Substitution =
        Substitution(
            mergeMaps(typeSubst, other.typeSubst),
            mergeMaps(regionSubst, other.regionSubst),
            mergeMaps(constSubst, other.constSubst)
        )

    operator fun get(key: TyTypeParameter): Ty? = typeSubst[key]
    operator fun get(psi: RsTypeParameter): Ty? = typeSubst[TyTypeParameter.named(psi)]

    operator fun get(key: ReEarlyBound): Region? = regionSubst[key]
    operator fun get(psi: RsLifetimeParameter): Region? = regionSubst[ReEarlyBound(psi)]

    operator fun get(key: CtConstParameter): Const? = constSubst[key]
    operator fun get(psi: RsConstParameter): Const? = constSubst[CtConstParameter(psi)]

    fun typeByName(name: String): Ty =
        typeSubst.entries.find { it.key.toString() == name }?.value ?: TyUnknown

    fun typeParameterByName(name: String): TyTypeParameter? =
        typeSubst.keys.find { it.toString() == name }

    fun substituteInValues(map: Substitution): Substitution =
        Substitution(
            typeSubst.mapValues { (_, value) -> value.substitute(map) },
            regionSubst.mapValues { (_, value) -> value.substitute(map) },
            constSubst.mapValues { (_, value) -> value.substitute(map) }
        )

    fun foldValues(folder: TypeFolder): Substitution =
        Substitution(
            typeSubst.mapValues { (_, value) -> value.foldWith(folder) },
            regionSubst.mapValues { (_, value) -> value.foldWith(folder) },
            constSubst.mapValues { (_, value) -> value.foldWith(folder) }
        )

    fun zipTypeValues(other: Substitution): List<Pair<Ty, Ty>> = zipValues(typeSubst, other.typeSubst)

    fun zipConstValues(other: Substitution): List<Pair<Const, Const>> = zipValues(constSubst, other.constSubst)

    fun mapTypeValues(transform: (Map.Entry<TyTypeParameter, Ty>) -> Ty): Substitution =
        Substitution(typeSubst.mapValues(transform), regionSubst, constSubst)

    fun mapConstValues(transform: (Map.Entry<CtConstParameter, Const>) -> Const): Substitution =
        Substitution(typeSubst, regionSubst, constSubst.mapValues(transform))

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

fun Map<TyTypeParameter, Ty>.toTypeSubst(): Substitution = Substitution(typeSubst = this)

private fun <K, V> mergeMaps(map1: Map<K, V>, map2: Map<K, V>): Map<K, V> =
    if (map1.isEmpty() && map2.isEmpty()) emptyMap() else HashMap(map1).apply { putAll(map2) }
