package org.rust.lang.core.types.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.pathTo
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.util.resolvedType
import org.rust.utils.cast
import java.util.*


object RustTypeInferenceEngine {

    fun inferPatBindingTypeFrom(binding: RustPatBindingElement, pat: RustPatElement, patType: RustType): RustType {
        check(pat.contains(binding))

        return RustTypeInferencingVisitor.runFrom(binding, pat, patType)
    }

}

@Suppress("IfNullToElvis")
private class RustTypeInferencingVisitor(val next: RustCompositeElement?, val type: RustType) : RustComputingVisitor<RustType>() {

    companion object {
        fun runFrom(binding: RustPatBindingElement, pat: RustPatElement, type: RustType): RustType {
            var curType = type

            val path = LinkedList(pat.pathTo(binding).cast<RustCompositeElement>().toList()) // sic!

            while (path.size >   1) {
                val cur     = path.pop()
                val next    = path.firstOrNull()

                curType = RustTypeInferencingVisitor(next, curType).compute(cur)

                // Bail-out
                if (curType is RustUnknownType) break
            }

            return curType
        }
    }

    override fun visitElement(element: PsiElement?) {
        throw UnsupportedOperationException("Panic! Unhandled pattern detected!")
    }

    override fun visitPatIdent(o: RustPatIdentElement) = set {
        if (o.patBinding === next) type else RustUnknownType
    }

    override fun visitPatTup(o: RustPatTupElement) = set(fun(): RustType {
        val i = o.patList.indexOf(next)

        check(i != -1)

        return set@ if (type is RustTupleType) type[i] else RustUnknownType
    })

    private fun getEnumByVariant(e: RustEnumVariantElement): RustEnumItemElement? =
        (e.parent as RustEnumBodyElement).parent as? RustEnumItemElement

    override fun visitPatEnum(o: RustPatEnumElement) = set(fun(): RustType {
        //
        // `pat_enum` perfectly covers 2 following destructuring scenarios:
        //
        //      > Named-tuple structs       [1]
        //      > Named-tuple enum-variants [2]
        //
        //
        //      ```
        //      // [1]
        //      struct S(i32);
        //
        //      // [2]
        //      enum E {
        //          X(i32)
        //      }
        //
        //      fn foo(x: E::X, s: S) {
        //          let E::X(i) = x     // Both of those `pat`s are `pat_enum`s
        //          let S(j) = s;       //
        //      }
        //      ```
        //

        var tupleFields: List<RustTupleFieldDeclElement>? = null

        val e = o.pathExpr.path.reference.resolve()
        if (e is RustStructItemElement) {
            val struct = e

            if (type !is RustStructType || type.struct !== struct)
                return set@RustUnknownType

            if (o.patList.size == 0) {
                // If pat-list is empty report type as the enum's itself
                return set@ type
            }

            tupleFields = struct.structTupleArgs?.tupleFieldDeclList

        } else if (e is RustEnumVariantElement) {
            val variant = e

            if (type !is RustEnumType || type.enum !== getEnumByVariant(variant))
                return set@RustUnknownType

            if (o.patList.size == 0) {
                // If pat-list is empty report type as the enum's itself
                return set@ type
            }

            tupleFields = variant.enumTupleArgs?.tupleFieldDeclList
        }

        if (tupleFields != null) {
            val i = o.patList.indexOf(next)

            check(i != -1)

            return set@ if (i < tupleFields.size) tupleFields[i].type.resolvedType else RustUnknownType
        }

        return set@RustUnknownType
    })

    override fun visitPatStruct(o: RustPatStructElement) = set(fun(): RustType {
        if (type !is RustStructType || type.struct !== o.pathExpr.path.reference.resolve())
            return RustUnknownType

        if (next is RustPatFieldElement) {
            val id = next.identifier?.text
            if (id == null)
                return set@RustUnknownType

            val fieldDecl = type.struct.fields.find { it.name == id }
            if (fieldDecl == null)
                return RustUnknownType

            return fieldDecl.type?.let { it.resolvedType } ?: RustUnknownType
        }

        return RustUnknownType
    })

    override fun visitPatRef(o: RustPatRefElement) = set(fun(): RustType {
        return set@
            if (type is RustReferenceType && type.mutable == (o.mut != null))
                type.referenced
            else
                RustUnknownType
    })

    override fun visitPatUniq(o: RustPatUniqElement) = set(fun(): RustType {
        // TODO(XXX): Fix me
        return set@ RustUnknownType
    })

    override fun visitPatVec(o: RustPatVecElement) = set(fun(): RustType {
        // TODO(XXX): Fix me
        return set@ RustUnknownType
    })
}
