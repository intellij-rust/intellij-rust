/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.joinToWithBuffer

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
val RsTypeReference.stubOnlyText: String
    get() = renderTypeReference(this)

/** Return text of the element without switching to AST (loses non-stubbed parts of PSI) */
val RsValueParameterList.stubOnlyText: String
    get() = renderValueParameterList(this)

private fun renderValueParameterList(list: RsValueParameterList): String {
    return buildString {
        append("(")
        val selfParameter = list.selfParameter
        val valueParameterList = list.valueParameterList
        if (selfParameter != null) {
            val typeReference = selfParameter.typeReference
            if (typeReference != null) {
                append("self: ")
                appendTypeReference(typeReference)
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
            typeReference?.let { sb.appendTypeReference(it) }
        }
        append(")")
    }
}

private fun renderTypeReference(ref: RsTypeReference): String =
    buildString { appendTypeReference(ref) }

private fun StringBuilder.appendTypeReference(ref: RsTypeReference) {
    when (val type = ref.typeElement) {
        is RsTupleType -> type.typeReferenceList.joinToWithBuffer(this, ", ", "(", ")") { it.appendTypeReference(this) }

        is RsBaseType -> when (val kind = type.kind) {
            RsBaseTypeKind.Unit -> append("()")
            RsBaseTypeKind.Never -> append("!")
            RsBaseTypeKind.Underscore -> append("_")
            is RsBaseTypeKind.Path -> appendPath(kind.path)
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
            appendTypeReference(type.typeReference)
        }

        is RsArrayType -> {
            append("[")
            appendTypeReference(type.typeReference)
            if (!type.isSlice) {
                append("; ")
                append(type.arraySize) // may trigger resolve
            }
            append("]")
        }

        is RsFnPointerType -> {
            append("fn")
            appendValueParameterListTypes(type.valueParameterList.valueParameterList)
            appendRetType(type.retType)
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
                    bound.traitRef?.path?.let { sb.appendPath(it) }
                }
            }
        }
    }
}

private fun StringBuilder.appendPath(path: RsPath) {
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
            typeReferenceList.joinToWithBuffer(this, ", ") { it.appendTypeReference(this) }
            if (assocTypeBindingList.isNotEmpty()) {
                append(", ")
            }
        }
        assocTypeBindingList.joinToWithBuffer(this, ", ") { sb ->
            sb.append(referenceName)
            sb.append("=")
            typeReference?.let { sb.appendTypeReference(it) }
        }
        append(">")
    } else if (fnSugar != null) {
        appendValueParameterListTypes(fnSugar.valueParameterList)
        appendRetType(path.retType)
    }
}

private fun StringBuilder.appendRetType(retType: RsRetType?) {
    val retTypeRef = retType?.typeReference
    if (retTypeRef != null) {
        append(" -> ")
        appendTypeReference(retTypeRef)
    }
}

private fun StringBuilder.appendValueParameterListTypes(list: List<RsValueParameter>) {
    list.joinToWithBuffer(this, separator = ", ", prefix = "(", postfix = ")") { sb ->
        typeReference?.let { sb.appendTypeReference(it) }
    }
}
