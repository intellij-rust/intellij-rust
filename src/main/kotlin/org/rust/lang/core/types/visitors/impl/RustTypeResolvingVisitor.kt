package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

open class RustTypeResolvingVisitor(private val pivot: RustCompositeElement) : RustUnresolvedTypeVisitor<RustType> {

    private fun visit(type: RustUnresolvedType): RustType = type.accept(this)

    override fun visitUnknown(type: RustUnknownType): RustType = RustUnknownType

    override fun visitUnitType(type: RustUnitType): RustType = RustUnitType

    override fun visitTupleType(type: RustUnresolvedTupleType): RustType =
        RustTupleType(type.elements.map { visit(it) })

    override fun visitPathType(type: RustUnresolvedPathType): RustType {
        val target = RustResolveEngine.resolve(type.path, pivot, Namespace.Types)
            .filterIsInstance<RustNamedElement>()
            .firstOrNull() ?: return RustUnknownType
        val typeArguments = (type.path as? RustPath.Named)?.head?.typeArguments.orEmpty()
        return RustTypificationEngine.typify(target)
            .withTypeArguments(typeArguments.map { it.resolvedType  })
    }

    override fun visitFunctionType(type: RustUnresolvedFunctionType): RustType =
        RustFunctionType(type.paramTypes.map { visit(it) }, visit(type.retType))

    override fun visitInteger(type: RustIntegerType): RustType = type

    override fun visitFloat(type: RustFloatType): RustType = type

    override fun visitString(type: RustStringSliceType): RustType = type

    override fun visitChar(type: RustCharacterType): RustType = type

    override fun visitBoolean(type: RustBooleanType): RustType = type

    override fun visitReference(type: RustUnresolvedReferenceType): RustType = RustReferenceType(visit(type.referenced), type.mutable)
}

