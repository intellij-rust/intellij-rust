package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.psi.ext.topLevelPattern
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.types.RustReferenceType
import org.rust.lang.core.types.types.RustStructType
import org.rust.lang.core.types.types.RustTupleType
import org.rust.lang.core.types.types.RustUnknownType

fun inferPatternBindingType(binding: RsPatBinding): RustType {
    val pattern = binding.topLevelPattern
        ?: error("Binding outside the pattern: ${binding.text}")
    val parent = pattern.parent
    val type = when (parent) {
        is RsLetDecl ->
            // use type ascription, if present or fallback to the type of the initializer expression
            parent.typeReference?.type ?: parent.expr?.type

        is RsValueParameter -> parent.typeReference?.type
        is RsCondition -> parent.expr.type
        is RsMatchArm -> parent.parentOfType<RsMatchExpr>()?.expr?.type
        is RsForExpr -> getIteratorItemType(parent.expr?.type ?: RustUnknownType, binding.project)
        else -> null
    } ?: RustUnknownType

    val bindings = collectBindings(pattern, type)
    return bindings[binding] ?: RustUnknownType
}

private fun collectBindings(pattern: RsPat, type: RustType): Map<RsPatBinding, RustType> {
    val bindings: MutableMap<RsPatBinding, RustType> = mutableMapOf()

    // Here, we try to infer as much types as possible, so we use
    // type only as a hint
    fun go(pat: RsPat, type: RustType) {
        when (pat) {
            is RsPatIdent -> {
                bindings += pat.patBinding to type
                pat.pat?.let { go(it, type) }
            }
            is RsPatTup -> {
                val types = (type as? RustTupleType)?.types.orEmpty()
                for ((idx, p) in pat.patList.withIndex()) {
                    go(p, types.getOrElse(idx, { RustUnknownType }))
                }
            }
            is RsPatEnum -> {
                // the type might actually be either a tuple variant of enum, or a tuple struct.
                val ref = pat.path.reference.resolve()
                val tupleFields = (ref as? RsFieldsOwner)?.tupleFields
                    ?: (type as? RustStructType)?.item?.tupleFields
                    ?: return

                for ((idx, p) in pat.patList.withIndex()) {
                    go(p, tupleFields.tupleFieldDeclList.getOrNull(idx)?.typeReference?.type ?: RustUnknownType)
                }
            }
            is RsPatStruct -> {
                val struct = pat.path.reference.resolve() as? RsFieldsOwner
                    ?: (type as? RustStructType)?.item
                    ?: return

                val structFields = struct.blockFields?.fieldDeclList?.associateBy { it.name }.orEmpty()
                for (patField in pat.patFieldList) {
                    val fieldPun = patField.patBinding
                    val fieldName = if (fieldPun != null) {
                        // Foo { bar }
                        fieldPun.identifier.text
                    } else {
                        patField.identifier?.text
                            ?: error("`pat_field` may be either `pat_binding` or should contain identifier! ${patField.text}")
                    }
                    val fieldType = structFields[fieldName]?.typeReference?.type ?: RustUnknownType
                    patField.pat?.let { go(it, fieldType) }
                    if (fieldPun != null) {
                        bindings[fieldPun] = fieldType
                    }
                }
            }
            is RsPatRef -> go(pat.pat, (type as? RustReferenceType)?.referenced ?: RustUnknownType)
            else -> {
                // not yet handled
            }
        }
    }

    go(pattern, type)
    return bindings
}

private fun getIteratorItemType(iteratorType: RustType, project: Project): RustType {
    val impl = RsImplIndex
        .findImplsFor(iteratorType, project)
        .find {
            val traitName = it.traitRef?.resolveToTrait?.name
            traitName == "Iterator" || traitName == "IntoIterator"
        } ?: return RustUnknownType

    val rawType = impl
        .typeAliasList
        .find { it.name == "Item" }
        ?.typeReference?.type ?: RustUnknownType

    val typeParameterMap = impl.remapTypeParameters(iteratorType.typeParameterValues)
    return rawType.substitute(typeParameterMap)
}
