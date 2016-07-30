package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.RustFunctionType
import org.rust.lang.core.types.RustReferenceType
import org.rust.lang.core.types.RustTupleType
import org.rust.lang.core.types.RustType

abstract class RustRecursiveTypeVisitorWithDefaults<T>(private val kind: TraversalKind) : RustTypeVisitorWithDefaults<T>() {

    enum class TraversalKind {
        PreOrder, PostOrder
    }

    private fun dispatchProperly(type: RustType, descendants: Iterable<RustType>): T {
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

    protected open fun visitTypeList(types: Iterable<RustType>): Iterable<T> =
        types.map { it.accept(this) }

    override fun visitTupleType(type: RustTupleType): T =
        dispatchProperly(type, descendants = type.types)

    override fun visitFunctionType(type: RustFunctionType): T =
        dispatchProperly(type, descendants = type.paramTypes + type.retType)

    override fun visitReference(type: RustReferenceType): T =
        dispatchProperly(type, descendants = listOf(type.referenced))


}
