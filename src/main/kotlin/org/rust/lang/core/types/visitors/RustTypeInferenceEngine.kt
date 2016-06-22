package org.rust.lang.core.types.visitors

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.indexOf
import org.rust.lang.core.psi.util.pathTo
import org.rust.lang.core.types.*
import org.rust.lang.core.types.util.resolvedType
import java.util.*


object RustTypeInferenceEngine {

    fun inferPatBindingTypeFrom(binding: RustPatBindingElement, pat: RustPatElement, patType: RustType): RustType {
        check(pat.contains(binding))

        return patType.accept(RustTypeInferencingVisitor(binding, pat))
    }

}

@Suppress("IfNullToElvis")
private class RustTypeInferencingVisitor(
    val binding: RustPatBindingElement,
    val pat: RustPatElement
) : RustTypeVisitor<RustType> {

    var path = LinkedList(pat.pathTo(binding).toList()) // sic!

    override fun visitUnitType(type: RustUnitType): RustType {
        val tip = path.firstOrNull() ?: return RustUnknownType
        return if (tip is RustPatIdentElement && tip.patBinding === binding && path.size == 2)
            RustUnitType
        else
            RustUnknownType
    }

    override fun visitTupleType(type: RustTupleType): RustType {
        val tip = path.firstOrNull()
        if (tip != null && tip is RustPatTupElement) {
            path.pop()

            val next = path.firstOrNull() as? RustPatElement
            if (next != null) {
                val i = tip.indexOf(next)
                return type.elements.elementAt(i).accept(this)
            }
        }

        return RustUnknownType
    }

    override fun visitStruct(type: RustStructType): RustType {
        val tip = path.firstOrNull() ?: return RustUnknownType
        if (tip is RustPatIdentElement) {
            if (tip.patBinding === binding) {
                return if (path.size == 2) type else RustUnknownType
            } else {
                return tip.pat?.let { pat ->
                    RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pat, type)
                } ?: RustUnknownType
            }
        } else if (tip is RustPatStructElement) {
            path.pop()

            if (tip.pathExpr.resolvedType != type)
                return RustUnknownType

            val next = path.firstOrNull() as? RustPatFieldElement
            if (next != null) {
                val id = next.identifier?.text
                if (id == null)
                    return RustUnknownType

                val fieldDecl = type.struct.fields.find { it.name == id }
                if (fieldDecl == null)
                    return RustUnknownType

                return fieldDecl.type?.let { ty ->
                    next.pat?.let { pat ->
                        RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pat, ty.resolvedType)
                    }
                } ?: RustUnknownType
            }
        }

        return RustUnknownType
    }

    override fun visitFunctionType(type: RustFunctionType): RustType {
        val tip = path.firstOrNull() ?: return RustUnknownType
        if (tip is RustPatIdentElement) {
            if (tip.patBinding === binding)
                return if (path.size == 2) type else RustUnknownType
        }

        return RustUnknownType
    }

    override fun visitInteger(type: RustIntegerType): RustType {
        val tip = path.firstOrNull() ?: return RustUnknownType
        return if (tip is RustPatIdentElement && tip.patBinding === binding && path.size == 2)
            type
        else
            RustUnknownType
    }

    override fun visitUnknown(type: RustUnknownType): RustType {
        return RustUnknownType
    }

}

