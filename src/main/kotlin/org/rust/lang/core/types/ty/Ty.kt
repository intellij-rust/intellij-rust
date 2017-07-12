/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.STD_DERIVABLE_TRAITS
import org.rust.lang.core.resolve.findDerefTarget
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.remapTypeParameters

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
    fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping? = null): Boolean

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

fun Ty.derefTransitively(project: Project): Set<Ty> {
    val result = mutableSetOf<Ty>()

    var ty = this
    while (true) {
        if (ty in result) break
        result += ty
        ty = if (ty is TyReference) {
            ty.referenced
        } else {
            findDerefTarget(project, ty)
                ?: break
        }
    }

    return result
}

fun Ty.getTypeParameter(name: String): TyTypeParameter? {
    return typeParameterValues.keys.find { it.toString() == name }
}

fun findImplsAndTraits(project: Project, ty: Ty): Collection<BoundElement<RsTraitOrImpl>> {
    return when (ty) {
        is TyTypeParameter -> ty.getTraitBoundsTransitively()
        is TyTraitObject -> BoundElement(ty.trait).flattenHierarchy

    //  XXX: TyStr is TyPrimitive, but we want to handle it separately
        is TyStr -> RsImplIndex.findImpls(project, ty).map { impl -> BoundElement(impl) }
        is TyUnit, is TyUnknown -> emptyList()

        else -> {
            val derived = (ty as? TyStructOrEnumBase)?.item?.derivedTraits.orEmpty()
                // select only std traits because we are sure
                // that they are resolved correctly
                .filter { item ->
                    val derivableTrait = STD_DERIVABLE_TRAITS[item.name] ?: return@filter false
                    item.containingCargoPackage?.origin == PackageOrigin.STDLIB &&
                        item.containingMod?.modName == derivableTrait.modName
                }.map { BoundElement(it, mapOf(TyTypeParameter(it) to ty)) }

            derived + RsImplIndex.findImpls(project, ty).map { impl ->
                BoundElement(impl, impl.remapTypeParameters(ty.typeParameterValues).orEmpty())
            }
        }
    }
}

fun findMethodsAndAssocFunctions(project: Project, ty: Ty): List<BoundElement<RsFunction>> {
    return findImplsAndTraits(project, ty).flatMap { it.functionsWithInherited }
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

fun Substitution.reverse(): Substitution =
    mapNotNull { if (it.value is TyTypeParameter) it else null }
        .associate { (k, v) -> Pair(v as TyTypeParameter, k) }
