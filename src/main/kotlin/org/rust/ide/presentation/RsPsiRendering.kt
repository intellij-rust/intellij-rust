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
fun RsTypeReference.getStubOnlyText(subst: Substitution = emptySubstitution, renderLifetimes: Boolean = true): String =
    renderTypeReference(this, subst, renderLifetimes)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsValueParameterList.getStubOnlyText(subst: Substitution = emptySubstitution, renderLifetimes: Boolean = true): String =
    renderValueParameterList(this, subst, renderLifetimes)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
fun RsTraitRef.getStubOnlyText(subst: Substitution = emptySubstitution, renderLifetimes: Boolean = true): String =
    buildString { appendPath(path, subst, renderLifetimes) }

private fun renderValueParameterList(list: RsValueParameterList, subst: Substitution, renderLifetimes: Boolean): String {
    return buildString {
        append("(")
        val selfParameter = list.selfParameter
        val valueParameterList = list.valueParameterList
        if (selfParameter != null) {
            val typeReference = selfParameter.typeReference
            if (typeReference != null) {
                append("self: ")
                appendTypeReference(typeReference, subst, renderLifetimes)
            } else {
                if (selfParameter.isRef) {
                    append("&")
                    val lifetime = selfParameter.lifetime
                    if (renderLifetimes && lifetime != null) {
                        append(lifetime.referenceName)
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
            typeReference?.let { sb.appendTypeReference(it, subst, renderLifetimes) }
        }
        append(")")
    }
}

private fun renderTypeReference(ref: RsTypeReference, subst: Substitution, renderLifetimes: Boolean): String =
    buildString { appendTypeReference(ref, subst, renderLifetimes) }

private fun StringBuilder.appendTypeReference(ref: RsTypeReference, subst: Substitution, renderLifetimes: Boolean) {
    val ty = ref.type
    if (ty is TyTypeParameter && subst[ty] != null) {
        append(ref.substAndGetText(subst))
        return
    }

    when (val type = ref.typeElement) {
        is RsTupleType ->
            type.typeReferenceList.joinToWithBuffer(this, ", ", "(", ")") {
                it.appendTypeReference(this, subst, renderLifetimes)
            }

        is RsBaseType -> when (val kind = type.kind) {
            RsBaseTypeKind.Unit -> append("()")
            RsBaseTypeKind.Never -> append("!")
            RsBaseTypeKind.Underscore -> append("_")
            is RsBaseTypeKind.Path -> appendPath(kind.path, subst, renderLifetimes)
        }

        is RsRefLikeType -> {
            if (type.isPointer) {
                append(if (type.mutability.isMut) "*mut " else "*const ")
            } else if (type.isRef) {
                append("&")
                val lifetime = type.lifetime
                if (renderLifetimes && lifetime != null) {
                    append(lifetime.referenceName)
                    append(" ")
                }
                if (type.mutability.isMut) append("mut ")
            }
            appendTypeReference(type.typeReference, subst, renderLifetimes)
        }

        is RsArrayType -> {
            append("[")
            appendTypeReference(type.typeReference, subst, renderLifetimes)
            if (!type.isSlice) {
                append("; ")
                append(type.arraySize) // may trigger resolve
            }
            append("]")
        }

        is RsFnPointerType -> {
            append("fn")
            appendValueParameterListTypes(type.valueParameters, subst, renderLifetimes)
            appendRetType(type.retType, subst, renderLifetimes)
        }

        is RsTraitType -> {
            append(if (type.isImpl) "impl " else "dyn ")
            type.polyboundList.joinToWithBuffer(this, " + ") { sb ->
                val forLifetimes = forLifetimes
                if (renderLifetimes && forLifetimes != null) {
                    sb.append("for<")
                    forLifetimes.lifetimeParameterList.joinTo(sb, ", ") {
                        it.name ?: "'_"
                    }
                    sb.append("> ")
                }

                val bound = bound
                val lifetime = bound.lifetime
                if (renderLifetimes && lifetime != null) {
                    sb.append(lifetime.referenceName)
                } else {
                    bound.traitRef?.path?.let { sb.appendPath(it, subst, renderLifetimes) }
                }
            }
        }
    }
}

private fun StringBuilder.appendPath(path: RsPath, subst: Substitution, renderLifetimes: Boolean) {
    append(path.referenceName)
    val inAngles = path.typeArgumentList // Foo<...>
    val fnSugar = path.valueParameterList // &dyn FnOnce(...) -> i32
    if (inAngles != null) {
        val lifetimeList = inAngles.lifetimeList
        val typeReferenceList = inAngles.typeReferenceList
        val assocTypeBindingList = inAngles.assocTypeBindingList

        val hasLifetimes = renderLifetimes && lifetimeList.isNotEmpty()
        val hasTypeReferences = typeReferenceList.isNotEmpty()
        val hasAssocTypeBindings = assocTypeBindingList.isNotEmpty()

        if (hasLifetimes || hasTypeReferences || hasAssocTypeBindings) {
            append("<")
            if (hasLifetimes) {
                lifetimeList.joinToWithBuffer(this, ", ") { it.append(referenceName) }
                if (hasTypeReferences || hasAssocTypeBindings) {
                    append(", ")
                }
            }
            if (hasTypeReferences) {
                typeReferenceList.joinToWithBuffer(this, ", ") { it.appendTypeReference(this, subst, renderLifetimes) }
                if (hasAssocTypeBindings) {
                    append(", ")
                }
            }
            assocTypeBindingList.joinToWithBuffer(this, ", ") { sb ->
                sb.append(referenceName)
                sb.append("=")
                typeReference?.let { sb.appendTypeReference(it, subst, renderLifetimes) }
            }
            append(">")
        }
    } else if (fnSugar != null) {
        appendValueParameterListTypes(fnSugar.valueParameterList, subst, renderLifetimes)
        appendRetType(path.retType, subst, renderLifetimes)
    }
}

private fun StringBuilder.appendRetType(retType: RsRetType?, subst: Substitution, renderLifetimes: Boolean) {
    val retTypeRef = retType?.typeReference
    if (retTypeRef != null) {
        append(" -> ")
        appendTypeReference(retTypeRef, subst, renderLifetimes)
    }
}

private fun StringBuilder.appendValueParameterListTypes(list: List<RsValueParameter>, subst: Substitution, renderLifetimes: Boolean) {
    list.joinToWithBuffer(this, separator = ", ", prefix = "(", postfix = ")") { sb ->
        typeReference?.let { sb.appendTypeReference(it, subst, renderLifetimes) }
    }
}
