package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*
import org.rust.lang.core.types.visitors.RustInvariantTypeVisitor
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor
import org.rust.utils.safely

open class RustEqualityTypeVisitor(override var lop: RustType)
    : RustEqualityTypeVisitorBase<RustType>()
    , RustTypeVisitor<Boolean> {

    protected fun visit(lop: RustType, rop: RustType): Boolean {
        val prev = this.lop
        this.lop = lop

        return safely({ rop.accept(this) }) {
            this.lop = prev
        }
    }

    protected fun visitTypeList(lop: Iterable<RustType>, rop: Iterable<RustType>): Boolean =
        lop.zip(rop).all({ visit(it.first, it.second) })

    override fun visitStruct(type: RustStructType): Boolean {
        val lop = lop
        if (lop !is RustStructType)
            return false

        return lop.item == type.item
    }

    override fun visitEnum(type: RustEnumType): Boolean {
        val lop = lop
        if (lop !is RustEnumType)
            return false

        return lop.item == type.item
    }

    override fun visitTupleType(type: RustTupleType): Boolean {
        val lop = lop
        if (lop !is RustTupleType || lop.size != type.size)
            return false

        return visitTypeList(lop.types, type.types)
    }

    override fun visitFunctionType(type: RustFunctionType): Boolean {
        val lop = lop
        if (lop !is RustFunctionType)
            return false

        return visit(lop.retType, type.retType) && visitTypeList(lop.paramTypes, type.paramTypes)
    }

    override fun visitTypeParameter(type: RustTypeParameterType): Boolean {
        val lop = lop
        if (lop !is RustTypeParameterType)
            return false

        return lop.parameter === type.parameter
    }

    override fun visitTrait(type: RustTraitType): Boolean {
        val lop = lop
        if (lop !is RustTraitType)
            return false

        return lop.trait == type.trait
    }

    override fun visitReference(type: RustReferenceType): Boolean {
        val lop = lop
        if (lop !is RustReferenceType)
            return false

        return lop.mutable == type.mutable && visit(lop.referenced, type.referenced)
    }
}


open class RustEqualityUnresolvedTypeVisitor(override var lop: RustUnresolvedType)
    : RustEqualityTypeVisitorBase<RustUnresolvedType>()
    , RustUnresolvedTypeVisitor<Boolean> {

    fun visit(lop: RustUnresolvedType, rop: RustUnresolvedType): Boolean {
        val prev = this.lop
        this.lop = lop

        return safely({ rop.accept(this) }) {
            this.lop = prev
        }
    }

    fun visitTypeList(lop: Iterable<RustUnresolvedType>, rop: Iterable<RustUnresolvedType>): Boolean =
        lop.count() == rop.count() && lop.zip(rop).all({ visit(it.first, it.second) })

    override fun visitPathType(type: RustUnresolvedPathType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedPathType)
            return false

        return lop.path == type.path
    }

    override fun visitTupleType(type: RustUnresolvedTupleType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedTupleType)
            return false

        return visitTypeList(lop.types, type.types)
    }

    override fun visitFunctionType(type: RustUnresolvedFunctionType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedFunctionType)
            return false

        return visit(lop.retType, type.retType) && visitTypeList(lop.paramTypes, type.paramTypes)
    }

    override fun visitReference(type: RustUnresolvedReferenceType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedReferenceType)
            return false

        return lop.mutable == type.mutable && visit(lop.referenced, type.referenced)
    }
}

abstract class RustEqualityTypeVisitorBase<T>() : RustInvariantTypeVisitor<Boolean> {

    protected abstract var lop: T

    override fun visitInteger(type: RustIntegerType): Boolean {
        val lop = lop
        return lop is RustIntegerType && lop.kind === type.kind
    }

    override fun visitFloat(type: RustFloatType): Boolean {
        val lop = lop
        return lop is RustFloatType && lop.kind == type.kind
    }

    override fun visitUnknown(type: RustUnknownType): Boolean {
        return lop === type
    }

    override fun visitUnitType(type: RustUnitType): Boolean {
        return lop === type
    }

    override fun visitString(type: RustStringType): Boolean {
        return lop === type
    }

    override fun visitChar(type: RustCharacterType): Boolean {
        return lop === type
    }

    override fun visitBoolean(type: RustBooleanType): Boolean {
        return lop === type
    }
}
