package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.resolve.findDerefTarget
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.types.*

interface RustType {
    /**
     * Checks if `other` type may be represented as this type.
     *
     * Note that `t1.canUnifyWith(t2)` is not the same as `t2.canUnifyWith(t1)`.
     */
    fun canUnifyWith(other: RustType, project: Project): Boolean

    /**
     * Apply positional type arguments to a generic type.
     *
     * This works for `some::path::<Type1, Type2>` case.
     */
    fun withTypeArguments(typeArguments: List<RustType>): RustType = this

    /**
     * Apply named type arguments to a generic type.
     *
     * This works for `struct S<T> { field: T }`, when we
     * know the type of `T` and want to find the type of `field`.
     */
    fun substitute(map: Map<RustTypeParameterType, RustType>): RustType = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    val typeParameterValues: Map<RustTypeParameterType, RustType> get() = emptyMap()

    override fun toString(): String

}

fun RustType.derefTransitively(project: Project): Set<RustType> {
    val result = mutableSetOf<RustType>()

    var ty = this
    while (true) {
        if (ty in result) break
        result += ty
        ty = if (ty is RustReferenceType) {
            ty.referenced
        } else {
            findDerefTarget(project, ty)
                ?: break
        }
    }

    return result
}

fun findImplsAndTraits(project: Project, ty: RustType): Pair<Collection<RsImplItem>, Collection<RsTraitItem>> {
    val noImpls = emptyList<RsImplItem>()
    val noTraits = emptyList<RsTraitItem>()
    return when (ty) {
        is RustTypeParameterType -> noImpls to ty.getTraitBoundsTransitively()
        is RustTraitType -> noImpls to ty.trait.flattenHierarchy
        is RustSliceType, is RustStringSliceType -> RsImplIndex.findImpls(project, ty) to emptyList()
        is RustPrimitiveType, is RustUnitType, is RustUnknownType -> noImpls to noTraits
        else -> RsImplIndex.findImpls(project, ty) to emptyList()
    }
}

fun findTraits(project: Project, ty: RustType): Collection<RsTraitItem> {
    val (impls, traits) = findImplsAndTraits(project, ty)
    return traits + impls.mapNotNull { it.traitRef?.resolveToTrait }
}

fun findMethodsAndAssocFunctions(project: Project, ty: RustType): List<RsFunction> {
    val (impls, traits) = findImplsAndTraits(project, ty)
    return impls.flatMap { it.allMethodsAndAssocFunctions } + traits.flatMap { it.functionList }
}

/**
 * Checks whether this particular type is a primitive one
 */
val RustType.isPrimitive: Boolean get() = when (this) {
    is RustFloatType,
    is RustIntegerType,
    is RustBooleanType,
    is RustCharacterType,
    is RustArrayType,
    is RustSliceType,
    is RustStringSliceType -> true
    else -> false
}

private val RsImplItem.allMethodsAndAssocFunctions: Collection<RsFunction> get() {
    val directlyImplemented = functionList.map { it.name }.toSet()
    val defaulted = traitRef?.resolveToTrait?.functionList.orEmpty().asSequence().filter {
        it.name !in directlyImplemented
    }

    return functionList + defaulted
}
