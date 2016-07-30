package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*
import org.rust.lang.core.types.visitors.RustInvariantTypeVisitor
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor
import org.rust.utils.safely

class RustEqualityTypeVisitor(override var lop: RustType)
    : RustEqualityTypeVisitorBase<RustType>()
    , RustTypeVisitor<Boolean> {

    fun visit(lop: RustType, rop: RustType): Boolean {
        val prev = this.lop
        this.lop = lop

        return safely({ rop.accept(this) }) {
            this.lop = prev
        }
    }

    fun visit(lop: Iterable<RustType>, rop: Iterable<RustType>): Boolean =
        lop.zip(rop).fold(true, { r, p -> r && visit(p.first, p.second) })

    override fun visitStruct(type: RustStructType): Boolean {
        val lop = lop
        if (lop !is RustStructType)
            return false

        return lop.struct == type.struct
    }

    override fun visitEnum(type: RustEnumType): Boolean {
        val lop = lop
        if (lop !is RustEnumType)
            return false

        return lop.enum == type.enum
    }

    override fun visitTupleType(type: RustTupleType): Boolean {
        val lop = lop
        if (lop !is RustTupleType || lop.size != type.size)
            return false

        return visit(lop.types, type.types)
    }

    override fun visitFunctionType(type: RustFunctionType): Boolean {
        val lop = lop
        if (lop !is RustFunctionType)
            return false

        return visit(lop.retType, type.retType) && visit(lop.paramTypes, type.paramTypes)
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

    fun visit(lop: Iterable<RustUnresolvedType>, rop: Iterable<RustUnresolvedType>): Boolean =
        lop.zip(rop).fold(true, { r, p -> r && visit(p.first, p.second) })

    override fun visitPathType(type: RustUnresolvedPathType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedPathType)
            return false

        return lop.path.equals(type.path)
    }

    override fun visitTupleType(type: RustUnresolvedTupleType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedTupleType)
            return false

        return visit(lop.types, type.types)
    }

    override fun visitFunctionType(type: RustUnresolvedFunctionType): Boolean {
        val lop = lop
        if (lop !is RustUnresolvedFunctionType)
            return false

        return visit(lop.retType, type.retType) && visit(lop.paramTypes, type.paramTypes)
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

    override fun visitUnknown(type: RustUnknownType): Boolean {
        return lop === type
    }

    override fun visitUnitType(type: RustUnitType): Boolean {
        return lop === type
    }

    override fun visitInteger(type: RustIntegerType): Boolean {
        val lop = lop
        return lop is RustIntegerType && lop.kind === type.kind
    }

    override fun visitFloat(type: RustFloatType): Boolean {
        val lop = lop
        return lop is RustFloatType && lop.kind == type.kind
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
