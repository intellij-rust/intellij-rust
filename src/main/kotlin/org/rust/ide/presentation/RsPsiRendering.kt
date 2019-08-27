/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.stdext.joinToWithBuffer

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTypeReference.getStubOnlyText(subst: Substitution = emptySubstitution): String =
    renderTypeReference(this, subst)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsValueParameterList.getStubOnlyText(subst: Substitution = emptySubstitution): String =
    renderValueParameterList(this, subst)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTraitRef.getStubOnlyText(subst: Substitution = emptySubstitution): String =
    buildString { appendPath(path, subst) }

private fun renderValueParameterList(list: RsValueParameterList, subst: Substitution): String {
    return buildString {
        append("(")
        val selfParameter = list.selfParameter
        val valueParameterList = list.valueParameterList
        if (selfParameter != null) {
            val typeReference = selfParameter.typeReference
            if (typeReference != null) {
                append("self: ")
                appendTypeReference(typeReference, subst)
            } else {
                if (selfParameter.isRef) {
                    append("&")
                    selfParameter.lifetime?.let {
                        append(it.referenceName)
                        append(" ")
                    }
                    append(if (selfParameter.mutability.isMut) "mut " else "")
                }
                append("self")
            }
            if (valueParameterList.isNotEmpty()) {
                append(", ")
            }
        }
        valueParameterList.joinToWithBuffer(this, separator = ", ") { sb ->
            sb.append(patText ?: "_")
            sb.append(": ")
            typeReference?.let { sb.appendTypeReference(it, subst) }
        }
        append(")")
    }
}

private fun renderTypeReference(ref: RsTypeReference, subst: Substitution): String =
    buildString { appendTypeReference(ref, subst) }

private fun renderTraitRef(ref: RsTraitRef, subst: Substitution): String =
    buildString { appendPath(ref.path, subst) }

private fun StringBuilder.appendTypeReference(ref: RsTypeReference, subst: Substitution) {
    val ty = ref.type
    if (ty is TyTypeParameter && subst[ty] != null) {
        append(ref.substAndGetText(subst))
        return
    }

    when (val type = ref.typeElement) {
        is RsTupleType -> type.typeReferenceList.joinToWithBuffer(this, ", ", "(", ")") { it.appendTypeReference(this, subst) }

        is RsBaseType -> when (val kind = type.kind) {
            RsBaseTypeKind.Unit -> append("()")
            RsBaseTypeKind.Never -> append("!")
            RsBaseTypeKind.Underscore -> append("_")
            is RsBaseTypeKind.Path -> appendPath(kind.path, subst)
        }

        is RsRefLikeType -> {
            if (type.isPointer) {
                append(if (type.mutability.isMut) "*mut " else "*const ")
            } else if (type.isRef) {
                append("&")
                type.lifetime?.let {
                    append(it.referenceName)
                    append(" ")
                }
                if (type.mutability.isMut) append("mut ")
            }
            appendTypeReference(type.typeReference, subst)
        }

        is RsArrayType -> {
            append("[")
            appendTypeReference(type.typeReference, subst)
            if (!type.isSlice) {
                append("; ")
                append(type.arraySize) // may trigger resolve
            }
            append("]")
        }

        is RsFnPointerType -> {
            append("fn")
            appendValueParameterListTypes(type.valueParameters, subst)
            appendRetType(type.retType, subst)
        }

        is RsTraitType -> {
            append(if (type.isImpl) "impl " else "dyn ")
            type.polyboundList.joinToWithBuffer(this, " + ") { sb ->
                val forLifetimes = forLifetimes
                if (forLifetimes != null) {
                    sb.append("for<")
                    forLifetimes.lifetimeParameterList.joinTo(sb, ", ") {
                        it.name ?: "'_"
                    }
                    sb.append("> ")
                }

                val bound = bound
                val lifetime = bound.lifetime
                if (lifetime != null) {
                    sb.append(lifetime.referenceName)
                } else {
                    bound.traitRef?.path?.let { sb.appendPath(it, subst) }
                }
            }
        }
    }
}

private fun StringBuilder.appendPath(path: RsPath, subst: Substitution) {
    append(path.referenceName)
    val inAngles = path.typeArgumentList // Foo<...>
    val fnSugar = path.valueParameterList // &dyn FnOnce(...) -> i32
    if (inAngles != null) {
        append("<")
        val lifetimeList = inAngles.lifetimeList
        val typeReferenceList = inAngles.typeReferenceList
        val assocTypeBindingList = inAngles.assocTypeBindingList
        if (lifetimeList.isNotEmpty()) {
            lifetimeList.joinToWithBuffer(this, ", ") { it.append(referenceName) }
            if (typeReferenceList.isNotEmpty() || assocTypeBindingList.isNotEmpty()) {
                append(", ")
            }
        }
        if (typeReferenceList.isNotEmpty()) {
            typeReferenceList.joinToWithBuffer(this, ", ") { it.appendTypeReference(this, subst) }
            if (assocTypeBindingList.isNotEmpty()) {
                append(", ")
            }
        }
        assocTypeBindingList.joinToWithBuffer(this, ", ") { sb ->
            sb.append(referenceName)
            sb.append("=")
            typeReference?.let { sb.appendTypeReference(it, subst) }
        }
        append(">")
    } else if (fnSugar != null) {
        appendValueParameterListTypes(fnSugar.valueParameterList, subst)
        appendRetType(path.retType, subst)
    }
}

private fun StringBuilder.appendRetType(retType: RsRetType?, subst: Substitution) {
    val retTypeRef = retType?.typeReference
    if (retTypeRef != null) {
        append(" -> ")
        appendTypeReference(retTypeRef, subst)
    }
}

private fun StringBuilder.appendValueParameterListTypes(list: List<RsValueParameter>, subst: Substitution) {
    list.joinToWithBuffer(this, separator = ", ", prefix = "(", postfix = ")") { sb ->
        typeReference?.let { sb.appendTypeReference(it, subst) }
    }
}
