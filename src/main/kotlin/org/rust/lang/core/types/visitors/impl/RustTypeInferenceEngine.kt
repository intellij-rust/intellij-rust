package org.rust.lang.core.types.visitors.impl

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.util.resolvedType
import org.rust.utils.Result


object RustTypeInferenceEngine {

    fun inferPatBindingTypeFrom(binding: RustPatBindingElement, pat: RustPatElement, type: RustType): RustType {
        check(pat.contains(binding))

        return run(pat, type).let {
            if (it is Result.Ok)
                it.result.getOrElse(binding, { error("Panic! Successful match should imbue all the bindings!") })
            else
                RustUnknownType
        }
    }

    fun matches(pat: RustPatElement, type: RustType): Boolean =
        run(pat, type).let { it is Result.Ok }

    private fun run(pat: RustPatElement, type: RustType): Result<Map<RustPatBindingElement, RustType>> =
        RustTypeInferencingVisitor(type).let {
            if (it.compute(pat)) Result.Ok(it.bindings) else Result.Failure
        }
}

@Suppress("IfNullToElvis")
private class RustTypeInferencingVisitor(var type: RustType) : RustComputingVisitor<Boolean>() {

    val bindings: MutableMap<RustPatBindingElement, RustType> = hashMapOf()

    private fun match(pat: RustPatElement, type: RustType): Boolean {
        val prev = this.type

        this.type = type
        try {
            return compute(pat)
        } finally {
            this.type = prev
        }
    }

    override fun visitElement(element: PsiElement?) {
        throw UnsupportedOperationException("Panic! Unhandled pattern detected!")
    }

    override fun visitPatIdent(o: RustPatIdentElement) = set(fun(): Boolean {
        val pat = o.pat
        if (pat == null || match(pat, type)) {
            bindings += o.patBinding to type
            return true
        }

        return false
    })

    override fun visitPatWild(o: RustPatWildElement) = set({ true })

    override fun visitPatTup(o: RustPatTupElement) = set(fun(): Boolean {
        val type = type
        if (type !is RustTupleType)
            return false

        val pats = o.patList
        if (pats.size != type.size)
            return false

        for (i in 0 .. type.size - 1) {
            if (!match(pats[i], type[i]))
                return false
        }

        return true
    })

    private fun getEnumByVariant(e: RustEnumVariantElement): RustEnumItemElement? =
        (e.parent as RustEnumBodyElement).parent as? RustEnumItemElement

    override fun visitPatEnum(o: RustPatEnumElement) = set(fun(): Boolean {
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

        val type = type

        var tupleFields = emptyList<RustTupleFieldDeclElement>()

        val e = o.pathExpr.path.reference.resolve()
        if (e is RustStructItemElement) {
            val struct = e

            if (type !is RustStructType || type.struct !== struct)
                return false

            struct.tupleFields?.tupleFieldDeclList?.let {
                tupleFields = it
            }

        } else if (e is RustEnumVariantElement) {
            val variant = e

            if (type !is RustEnumType || type.enum !== getEnumByVariant(variant))
                return false

            variant.tupleFields?.tupleFieldDeclList?.let {
                tupleFields = it
            }
        }

        if (tupleFields.size != o.patList.size)
            return false

        for (i in 0 .. tupleFields.size - 1) {
            if (!match(o.patList[i], tupleFields[i].type.resolvedType))
                return false
        }

        return true
    })

    override fun visitPatStruct(o: RustPatStructElement) = set(fun(): Boolean {
        val type = type
        if (type !is RustStructType || type.struct !== o.pathExpr.path.reference.resolve())
            return false

        val fieldDecls =
            type.struct.blockFields?.let {
                it.fieldDeclList.map { it.name to it }.toMap()
            } ?: emptyMap()

        // `..` allows to match for the struct's fields not to be exhaustive
        if (o.patFieldList.size != fieldDecls.size && o.dotdot == null)
            return false

        for (patField in o.patFieldList) {
            val patBinding = patField.patBinding
            if (patBinding != null) {
                val fieldDecl = fieldDecls[patBinding.identifier.text]
                if (fieldDecl == null)
                    return false

                bindings += patBinding to fieldDecl.type.resolvedType
            } else {
                val id = patField.identifier
                if (id == null)
                    error("Panic! `pat_field` may be either `pat_binding` or should contain identifier!")

                val patFieldPat = patField.pat
                if (patFieldPat == null)
                    return false

                val fieldDecl = fieldDecls[id.text]
                if (fieldDecl == null || !match(patFieldPat, fieldDecl.type.resolvedType))
                    return false
            }
        }

        return true
    })

    override fun visitPatRef(o: RustPatRefElement) = set(fun(): Boolean {
        val type = type
        return type is RustReferenceType
            && type.mutable == (o.mut != null)
            && match(o.pat, type.referenced)
    })

    override fun visitPatUniq(o: RustPatUniqElement) = set(fun(): Boolean {
        // FIXME
        return false
    })

    override fun visitPatVec(o: RustPatVecElement) = set(fun(): Boolean {
        // FIXME
        return false
    })
}
