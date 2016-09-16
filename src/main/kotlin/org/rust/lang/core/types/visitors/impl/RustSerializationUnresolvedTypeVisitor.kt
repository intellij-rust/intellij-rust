package org.rust.lang.core.types.visitors.impl

import com.intellij.util.containers.BidirectionalMap
import org.rust.lang.core.symbols.readRustPath
import org.rust.lang.core.symbols.writeRustPath
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*
import org.rust.lang.core.types.visitors.RustRecursiveUnresolvedTypeVisitorWithDefaults
import java.io.DataInput
import java.io.DataOutput


class RustSerializationUnresolvedTypeVisitor(private val output: DataOutput)
    : RustRecursiveUnresolvedTypeVisitorWithDefaults<Unit>(kind = TraversalKind.PreOrder) {

    fun visit(type: RustUnresolvedType) {
        type.accept(this)
    }

    override fun visitByDefault(type: RustUnresolvedType) {
        output.writeInt(encode(type))
    }

    override fun visitTypeList(types: Iterable<RustUnresolvedType>): Iterable<Unit> {
        types.toList().let {
            output.writeInt(it.size)
            return super.visitTypeList(it)
        }
    }

    override fun visitReference(type: RustUnresolvedReferenceType) {
        super.visitReference(type)

        output.writeBoolean(type.mutable)
    }

    override fun visitPathType(type: RustUnresolvedPathType) {
        super.visitPathType(type)

        output.writeRustPath(type.path)
    }

    override fun visitInteger(type: RustIntegerType) {
        super.visitInteger(type)

        output.writeInt(type.kind.ordinal)
    }

    override fun visitFloat(type: RustFloatType) {
        super.visitFloat(type)

        output.writeInt(type.kind.ordinal)
    }
}


class RustDeserializationUnresolvedTypeVisitor(private val input: DataInput) {

    /**
     * TODO(XXX): Brush up
     */
    class DeserializationException : Exception()

    private fun visitTypeList(): List<RustUnresolvedType> {
        val size    = input.readInt()
        val types   = arrayListOf<RustUnresolvedType>()

        for (i in (0 until size))
            types.add(visit())

        return types
    }

    fun visit(): RustUnresolvedType = when (decode(input.readInt())) {
        RustUnresolvedPathType::class.java      -> visitPathType()
        RustUnresolvedTupleType::class.java     -> visitTupleType()
        RustUnitType::class.java                -> visitUnitType()
        RustUnknownType::class.java             -> visitUnknownType()
        RustUnresolvedFunctionType::class.java  -> visitFunctionType()
        RustIntegerType::class.java             -> visitIntegerType()
        RustUnresolvedReferenceType::class.java -> visitReferenceType()
        RustFloatType::class.java               -> visitFloatType()
        RustStringType::class.java              -> visitStringType()
        RustCharacterType::class.java           -> visitCharacterType()
        RustBooleanType::class.java             -> visitBooleanType()

        else -> throw DeserializationException()
    }

    private fun visitReferenceType(): RustUnresolvedReferenceType {
        val referenced  = visitTypeList()[0]
        val mutable     = input.readBoolean()

        return RustUnresolvedReferenceType(referenced, mutable)
    }

    fun visitFunctionType(): RustUnresolvedFunctionType {
        val types = visitTypeList()
        return RustUnresolvedFunctionType(types.take(types.size - 1), types.last())
    }

    private fun visitTupleType(): RustUnresolvedTupleType {
        return RustUnresolvedTupleType(visitTypeList())
    }

    fun visitPathType(): RustUnresolvedPathType =
        RustUnresolvedPathType(input.readRustPath())

    private fun visitFloatType(): RustFloatType =
        RustFloatType(RustFloatType.Kind.values()[input.readInt()])

    fun visitIntegerType(): RustIntegerType =
        RustIntegerType(RustIntegerType.Kind.values()[input.readInt()])

    fun visitUnknownType(): RustUnknownType = RustUnknownType
    fun visitUnitType(): RustUnitType = RustUnitType
    fun visitBooleanType(): RustBooleanType = RustBooleanType
    fun visitCharacterType(): RustCharacterType = RustCharacterType
    fun visitStringType(): RustStringType = RustStringType

}

private fun decode(code: Int): Class<out RustUnresolvedType> =
    typeCodes.getKeysByValue(code)?.singleOrNull() ?:
        throw RustDeserializationUnresolvedTypeVisitor.DeserializationException()

private fun encode(type: RustUnresolvedType): Int =
    typeCodes[type.javaClass]!!

private val typeCodes =
    bidirectionalMapOf<Class<out RustUnresolvedType>, Int>(
        RustUnresolvedPathType::class.java      to 1022, // 1111111110
        RustUnresolvedTupleType::class.java     to 1023, // 1111111111
        RustUnitType::class.java                to 510,  // 111111110
        RustUnknownType::class.java             to 254,  // 11111110
        RustUnresolvedFunctionType::class.java  to 126,  // 1111110
        RustIntegerType::class.java             to 62,   // 111110
        RustUnresolvedReferenceType::class.java to 30,   // 11110
        RustFloatType::class.java               to 14,   // 1110
        RustStringType::class.java              to 6,    // 110
        RustCharacterType::class.java           to 2,    // 10
        RustBooleanType::class.java             to 0     // 0
    )

private fun <K, V> bidirectionalMapOf(vararg values: Pair<K, V>): BidirectionalMap<K, V> =
    BidirectionalMap<K, V>().let { map ->
        values.forEach { map.put(it.first, it.second) }
        map
    }
