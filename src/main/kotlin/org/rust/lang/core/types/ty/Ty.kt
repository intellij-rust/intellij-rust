package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.resolve.findDerefTarget
import org.rust.lang.core.resolve.indexes.RsImplIndex

typealias TypeArguments = Map<TyTypeParameter, Ty>
val emptyTypeArguments: TypeArguments = emptyMap()

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
    fun canUnifyWith(other: Ty, project: Project): Boolean

    /**
     * Apply positional type arguments to a type constructors.
     *
     * This works for `some::path::<Type1, Type2>` case.
     */
    fun applyTypeArguments(typeArguments: List<Ty>): Ty = this

    /**
     * Substitute type parameters for their values
     *
     * This works for `struct S<T> { field: T }`, when we
     * know the type of `T` and want to find the type of `field`.
     */
    fun substitute(map: TypeArguments): Ty = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    val typeParameterValues: TypeArguments get() = emptyTypeArguments

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

fun findImplsAndTraits(project: Project, ty: Ty): Pair<Collection<RsImplItem>, Collection<RsTraitItem>> {
    val noImpls = emptyList<RsImplItem>()
    val noTraits = emptyList<RsTraitItem>()
    return when (ty) {
        is TyTypeParameter -> noImpls to ty.getTraitBoundsTransitively()
        is TyTraitObject -> noImpls to ty.trait.flattenHierarchy
        is TySlice, is TyStr -> RsImplIndex.findImpls(project, ty) to emptyList()
        is TyPrimitive, is TyUnit, is TyUnknown -> noImpls to noTraits
        else -> RsImplIndex.findImpls(project, ty) to emptyList()
    }
}

fun findTraits(project: Project, ty: Ty): Collection<RsTraitItem> {
    val (impls, traits) = findImplsAndTraits(project, ty)
    return traits + impls.mapNotNull { it.traitRef?.resolveToTrait }
}

fun findMethodsAndAssocFunctions(project: Project, ty: Ty): List<RsFunction> {
    val (impls, traits) = findImplsAndTraits(project, ty)
    return impls.flatMap { it.allMethodsAndAssocFunctions } + traits.flatMap { it.functionList }
}

private val RsImplItem.allMethodsAndAssocFunctions: Collection<RsFunction> get() {
    val directlyImplemented = functionList.map { it.name }.toSet()
    val defaulted = traitRef?.resolveToTrait?.functionList.orEmpty().asSequence().filter {
        it.name !in directlyImplemented
    }

    return functionList + defaulted
}
