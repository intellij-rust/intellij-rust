/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.rust.ide.presentation.escaped
import org.rust.ide.presentation.presentableQualifiedName
import org.rust.ide.presentation.presentationInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.type
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsTypeParameter -> pre { generateDoc(element) }
        is RsDocAndAttributeOwner -> generateDoc(element)
        is RsPatBinding -> pre { generateDoc(element) }
        else -> null
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsPatBinding -> generateDoc(element)
        is RsTypeParameter -> generateDoc(element)
        is RsConstant -> element.presentationInfo?.quickDocumentationText
        is RsMod -> element.presentationInfo?.quickDocumentationText
        is RsItemElement -> element.header(false) + element.signature(false)
        is RsNamedElement -> element.presentationInfo?.quickDocumentationText
        else -> null
    }

    private fun generateDoc(element: RsDocAndAttributeOwner): String? {
        val doc = element.documentationAsHtml() ?: ""
        return element.header(true) + element.signature(true) + doc
    }

    private fun generateDoc(element: RsPatBinding): String? {
        val presentationInfo = element.presentationInfo ?: return null
        val type = element.type.toString().escaped
        return "${presentationInfo.type} <b>${presentationInfo.name}</b>: $type"
    }

    private fun generateDoc(element: RsTypeParameter): String? {
        val name = element.name ?: return null
        return buildString {
            append("type parameter ")
            b { it += name }
            val typeBounds = element.bounds
            if (typeBounds.isNotEmpty()) {
                typeBounds.joinTo(this, " + ", ": ") { generateDocumentation(it) }
            }
            element.typeReference?.generateDocumentation(this, " = ")
        }
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement? {
        if (context !is RsElement) return null
        return RsCodeFragmentFactory(context.project)
            .createPath(link, context)
            ?.reference
            ?.resolve()
    }
}

private fun RsDocAndAttributeOwner.header(usePreTag: Boolean): String {
    val rawLines = when (this) {
        is RsFieldDecl -> listOfNotNull((parent?.parent as? RsDocAndAttributeOwner)?.presentableQualifiedName)
        is RsFunction -> {
            val owner = owner
            when (owner) {
                is RsFunctionOwner.Foreign, is RsFunctionOwner.Free -> listOfNotNull(presentableQualifiedModName)
                is RsFunctionOwner.Impl ->
                    listOfNotNull(presentableQualifiedModName) + owner.impl.declarationText
                is RsFunctionOwner.Trait -> owner.trait.declarationText
            }
        }
        is RsStructOrEnumItemElement, is RsTraitItem -> listOfNotNull(presentableQualifiedModName)
        is RsTypeAlias -> {
            val owner = owner
            when (owner) {
                is RsTypeAliasOwner.Impl -> listOfNotNull(presentableQualifiedModName) + owner.impl.declarationText
                is RsTypeAliasOwner.Trait -> owner.trait.declarationText
                is RsTypeAliasOwner.Free -> listOfNotNull(presentableQualifiedModName)
            }
        }
        else -> listOfNotNull(presentableQualifiedName)
    }
    val startTag = if (usePreTag) "<pre>" else ""
    val endTag = if (usePreTag) "</pre>" else ""
    return when (rawLines.size) {
        0 -> ""
        1 -> "$startTag${rawLines[0]}$endTag\n"
        else -> {
            val firstLine = "$startTag${rawLines[0]}$endTag\n"
            val additionalLines = rawLines.drop(1).joinToString("<br>", startTag, endTag)
            "$firstLine$additionalLines\n"
        }
    }
}

private fun RsDocAndAttributeOwner.signature(usePreTag: Boolean): String {
    val rawLines = when (this) {
        is RsFieldDecl -> listOfNotNull(presentationInfo?.signatureText)
        is RsFunction -> {
            val buffer = StringBuilder()
            declarationModifiers.joinTo(buffer, " ")
            buffer += " "
            buffer.b { it += name }
            typeParameterList?.generateDocumentation(buffer)
            valueParameterList?.generateDocumentation(buffer)
            retType?.generateDocumentation(buffer)
            listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
        }
        // All these types extend RsTypeBearingItemElement and RsGenericDeclaration interfaces
        // so all casts are safe
        is RsStructOrEnumItemElement, is RsTraitItem, is RsTypeAlias -> {
            val name = name
            if (name != null) {
                val buffer = StringBuilder()
                (this as RsItemElement).declarationModifiers.joinTo(buffer, " ", "", " ")
                buffer.b { it += name }
                (this as RsGenericDeclaration).typeParameterList?.generateDocumentation(buffer)
                (this as? RsTypeAlias)?.typeReference?.generateDocumentation(buffer, " = ")
                listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
            } else emptyList()
        }
        else -> emptyList()
    }
    val startTag = if (usePreTag) "<pre>" else ""
    val endTag = if (usePreTag) "</pre>" else ""
    return if (rawLines.isNotEmpty()) rawLines.joinToString("<br>", startTag, "$endTag\n") else ""
}

private val RsImplItem.declarationText: List<String> get() {
    val typeRef = typeReference ?: return emptyList()

    val buffer = StringBuilder("impl")
    typeParameterList?.generateDocumentation(buffer)
    buffer += " "
    val traitRef = traitRef
    if (traitRef != null) {
        traitRef.generateDocumentation(buffer)
        buffer += " for "
    }
    typeRef.generateDocumentation(buffer)
    return listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
}

private val RsTraitItem.declarationText: List<String> get() {
    val name = presentableQualifiedName ?: return emptyList()
    val buffer = StringBuilder(name)
    typeParameterList?.generateDocumentation(buffer)
    return listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
}

private val RsItemElement.declarationModifiers: List<String> get() {
    val modifiers = mutableListOf<String>()
    if (isPublic) {
        modifiers += "pub"
    }
    when (this) {
        is RsFunction -> {
            if (isConst) {
                modifiers += "const"
            }
            if (isUnsafe) {
                modifiers += "unsafe"
            }
            if (isExtern) {
                modifiers += "extern"
                abiName?.let { modifiers += it }
            }
            modifiers += "fn"
        }
        is RsStructItem -> modifiers += "struct"
        is RsEnumItem -> modifiers += "enum"
        is RsTypeAlias -> modifiers += "type"
        is RsTraitItem -> {
            if (isUnsafe) {
                modifiers += "unsafe"
            }
            modifiers += "trait"
        }
        else -> error("unexpected type $javaClass")
    }
    return modifiers
}

private val RsWhereClause.documentationText: List<String> get() {
    return listOf("where") + wherePredList.mapNotNull {
        val buffer = StringBuilder()
        val lifetime = it.lifetime
        val typeReference = it.typeReference

        when {
            lifetime != null -> {
                lifetime.generateDocumentation(buffer)
                it.lifetimeParamBounds?.generateDocumentation(buffer)
            }
            typeReference != null -> {
                typeReference.generateDocumentation(buffer)
                it.typeParamBounds?.generateDocumentation(buffer)
            }
            else -> return@mapNotNull null
        }
        "&nbsp;&nbsp;&nbsp;&nbsp;$buffer,"
    }
}

private val RsDocAndAttributeOwner.presentableQualifiedModName: String? get() =
    presentableQualifiedName?.removeSuffix("::$name")

private fun PsiElement.generateDocumentation(buffer: StringBuilder, prefix: String = "", suffix: String = "") {
    buffer += prefix
    when (this) {
        is RsPath -> generatePathDocumentation(this, buffer)
        is RsAssocTypeBinding -> {
            buffer += identifier.text
            typeReference.generateDocumentation(buffer, " = ")
        }
        is RsTraitRef -> path.generateDocumentation(buffer)
        is RsLifetimeParamBounds -> lifetimeList.joinTo(buffer, " + ", ": ") { generateDocumentation(it) }
        is RsTypeParamBounds -> {
            if (polyboundList.isNotEmpty()) {
                polyboundList.joinTo(buffer, " + ", ": ") { generateDocumentation(it) }
            }
        }
        // TODO: support 'for lifetimes'
        is RsPolybound -> {
            if (q != null) {
                buffer += "?"
            }
            (bound.lifetime ?: bound.traitRef)?.generateDocumentation(buffer)
        }
        is RsTypeArgumentList -> (lifetimeList + typeReferenceList + assocTypeBindingList)
            .joinTo(buffer, ", ", "&lt;", "&gt;") { generateDocumentation(it) }
        is RsTypeParameterList -> (lifetimeParameterList + typeParameterList)
            .joinTo(buffer, ", ", "&lt;", "&gt;") { generateDocumentation(it) }
        is RsValueParameterList -> (listOfNotNull(selfParameter) + valueParameterList + listOfNotNull(dotdotdot))
            .joinTo(buffer, ", ", "(", ")") { generateDocumentation(it) }
        is RsLifetimeParameter -> {
            buffer += quoteIdentifier.text.escaped
            lifetimeParamBounds?.generateDocumentation(buffer)
        }
        is RsTypeParameter -> {
            buffer += name
            typeParamBounds?.generateDocumentation(buffer)
            typeReference?.generateDocumentation(buffer, " = ")
        }
        is RsValueParameter -> {
            pat?.generateDocumentation(buffer, suffix = ": ")
            typeReference?.generateDocumentation(buffer)
        }
        is RsTypeReference -> generateTypeReferenceDocumentation(this, buffer)
        is RsRetType -> typeReference?.generateDocumentation(buffer, " -&gt; ")
        is RsTypeQual -> {
            buffer += "&lt;"
            typeReference.generateDocumentation(buffer)
            traitRef?.generateDocumentation(buffer, " as ")
            buffer += "&gt;::"
        }
        else -> buffer += text.escaped
    }
    buffer += suffix
}

private fun generatePathDocumentation(element: RsPath, buffer: StringBuilder) {
    val path = element.path
    if (path != null) {
        buffer += path.text.escaped
        buffer += "::"
    }
    element.typeQual?.generateDocumentation(buffer)

    val name = element.referenceName
    if (element.isLinkNeeded()) {
        createLink(buffer, element.link(), name)
    } else {
        buffer += name
    }

    val typeArgumentList = element.typeArgumentList
    val valueParameterList = element.valueParameterList
    when {
        typeArgumentList != null -> typeArgumentList.generateDocumentation(buffer)
        valueParameterList != null -> {
            valueParameterList.generateDocumentation(buffer)
            element.retType?.generateDocumentation(buffer)
        }
    }
}

private fun generateTypeReferenceDocumentation(element: RsTypeReference, buffer: StringBuilder) {
    val typeElement = element.typeElement
    when (typeElement) {
        is RsBaseType -> {
            when {
                typeElement.isUnit -> buffer += "()"
                typeElement.isCself -> buffer += "Self"
                else -> typeElement.path?.generateDocumentation(buffer)
            }
        }
        is RsTupleType -> typeElement.typeReferenceList.joinTo(buffer, ", ", "(", ")") { generateDocumentation(it) }
        is RsArrayType -> {
            buffer += "["
            typeElement.typeReference.generateDocumentation(buffer)
            if (!typeElement.isSlice) {
                buffer += "; "
                buffer.append(typeElement.arraySize ?: "<unknown>")
            }
            buffer += "]"
        }
        is RsRefLikeType -> {
            if (typeElement.isRef) {
                buffer += "&amp;"
                typeElement.lifetime?.generateDocumentation(buffer, suffix = " ")
                if (typeElement.mutability.isMut) {
                    buffer += "mut "
                }
            } else {
                buffer += "*"
                buffer += if (typeElement.mutability.isMut) "mut " else "const "
            }
            typeElement.typeReference.generateDocumentation(buffer)
        }
        is RsFnPointerType -> {
            // TODO: handle abi
            buffer += "fn"
            typeElement.valueParameterList.generateDocumentation(buffer)
            typeElement.retType?.generateDocumentation(buffer)
        }
        else -> buffer += element.text.escaped
    }
}

private fun RsPath.isLinkNeeded(): Boolean {
    val element = reference.resolve()
    // If it'll find out that links for type parameters are useful
    // just check element for null
    return !(element == null || element is RsTypeParameter)
}

private fun RsPath.link(): String {
    val path = path
    val prefix = if (path != null) "${path.text.escaped}::" else typeQual?.text?.escaped
    return if (prefix != null) "$prefix$referenceName" else referenceName
}

private fun createLink(buffer: StringBuilder, refText: String, text: String) {
    DocumentationManagerUtil.createHyperlink(buffer, refText, text, true)
}

private inline fun pre(block: () -> String?): String? = block()?.let { "<pre>$it</pre>" }

private operator fun StringBuilder.plusAssign(value: String?) {
    if (value != null) {
        append(value)
    }
}

private inline fun StringBuilder.b(action: (StringBuilder) -> Unit) {
    append("<b>")
    action(this)
    append("</b>")
}

private inline fun <T> Iterable<T>.joinTo(
    buffer: StringBuilder,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    action: T.(StringBuilder) -> Unit
) {
    buffer.append(prefix)
    var needInsertSeparator = false
    for (element in this) {
        if (needInsertSeparator) {
            buffer.append(separator)
        }
        element.action(buffer)
        needInsertSeparator = true
    }
    buffer.append(postfix)
}
