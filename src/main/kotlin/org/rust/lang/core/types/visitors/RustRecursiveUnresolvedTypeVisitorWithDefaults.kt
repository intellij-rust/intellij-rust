package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.unresolved.RustUnresolvedFunctionType
import org.rust.lang.core.types.unresolved.RustUnresolvedReferenceType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.unresolved.RustUnresolvedType

abstract class RustRecursiveUnresolvedTypeVisitorWithDefaults<T>(private val kind: TraversalKind) : RustUnresolvedTypeVisitorWithDefaults<T>() {

    enum class TraversalKind {
        PreOrder, PostOrder
    }

    private fun dispatchProperly(type: RustUnresolvedType, descendants: Iterable<RustUnresolvedType>): T {
        return when (kind) {
            TraversalKind.PreOrder -> {
                val r = visitByDefault(type)
                visitTypeList(descendants)
                r
            }

            TraversalKind.PostOrder -> {
                visitTypeList(descendants)
                visitByDefault(type)
            }
        }
    }

    protected open fun visitTypeList(types: Iterable<RustUnresolvedType>): Iterable<T> =
        types.map { it.accept(this) }

    override fun visitTupleType(type: RustUnresolvedTupleType): T =
        dispatchProperly(type, descendants = type.types)

    override fun visitFunctionType(type: RustUnresolvedFunctionType): T =
        dispatchProperly(type, descendants = type.paramTypes + type.retType)

    override fun visitReference(type: RustUnresolvedReferenceType): T =
        dispatchProperly(type, descendants = listOf(type.referenced))

}
