package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.types.*

interface RustType {

    /**
     * Traits explicitly (or implicitly) implemented for this particular type
     */
    fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        RsImplIndex.findImplsFor(this, project).mapNotNull { it.traitRef?.trait }

    fun getMethodsIn(project: Project): Sequence<RsFunction> =
        RsImplIndex.findMethodsFor(this, project)

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

/**
 * Util to get through reference-types if any present
 */
fun RustType.stripAllRefsIfAny(): RustType = when (this) {
    is RustReferenceType -> referenced.stripAllRefsIfAny()
    else -> this
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
