/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.ide.utils.escaped
import org.rust.ide.utils.presentableQualifiedName
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsFunctionRole.*
import org.rust.lang.core.types.type
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsDocAndAttributeOwner -> generateDoc(element)
        is RsPatBinding -> pre { generateDoc(element) }
        is RsTypeParameter -> pre { generateDoc(element) }
        else -> null
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is RsPatBinding -> generateDoc(element)
        is RsTypeParameter -> generateDoc(element)
        is RsTypeBearingItemElement -> element.header(false) + element.signature(false)
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
        val typeBounds = element.bounds
        val bounds = if (typeBounds.isEmpty()) "" else typeBounds.joinToString(" + ", ": ") { it.text.escaped }
        val defaultValue = element.typeReference?.let { " = ${it.text}" } ?: ""
        return "type parameter <b>$name</b>$bounds$defaultValue"
    }
}

private fun RsDocAndAttributeOwner.header(usePreTag: Boolean): String {
    val rawLines = when (this) {
        is RsFieldDecl -> listOfNotNull((parent?.parent as? RsDocAndAttributeOwner)?.presentableQualifiedName)
        is RsFunction -> when (role) {
            FREE, FOREIGN -> listOfNotNull(presentableQualifiedModName)
            IMPL_METHOD -> listOfNotNull(presentableQualifiedModName) + (parent as? RsImplItem)?.declarationText.orEmpty()
            TRAIT_METHOD -> (parent as? RsTraitItem)?.declarationText.orEmpty()
        }
        is RsStructOrEnumItemElement, is RsTraitItem -> listOfNotNull(presentableQualifiedModName)
        is RsTypeAlias -> {
            val parent = parent
            when (parent) {
                is RsImplItem -> listOfNotNull(presentableQualifiedModName) + parent.declarationText
                is RsTraitItem -> parent.declarationText
                else -> listOfNotNull(presentableQualifiedModName)
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
            val shortSignature = presentationInfo?.shortSignatureText
            if (shortSignature != null) {
                val signatureParts = mutableListOf<String>()
                signatureParts += declarationModifiers
                signatureParts += shortSignature
                listOf(signatureParts.joinToString(" "))
            } else emptyList()
        }
        // All these types extend RsTypeBearingItemElement and RsGenericDeclaration interfaces
        // so all casts are safe
        is RsStructOrEnumItemElement, is RsTraitItem, is RsTypeAlias -> {
            val name = name
            if (name != null) {
                val signatureParts = mutableListOf<String>()
                signatureParts += (this as RsTypeBearingItemElement).declarationModifiers
                this as RsGenericDeclaration
                signatureParts += "<b>$name</b>${typeParameterList?.text?.escaped ?: ""}"
                val typeAliasValue = (this as? RsTypeAlias)?.typeReference
                if (typeAliasValue != null) {
                    signatureParts += "="
                    signatureParts += typeAliasValue.text.escaped
                }

                val lines = mutableListOf(signatureParts.joinToString(" "))
                lines += whereClause?.documentationText.orEmpty()
                lines
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
    val traitRef = traitRef
    val traitText = if (traitRef != null) "${traitRef.text} for " else ""
    val typeParams = typeParameterList?.text ?: ""
    val lines = mutableListOf("impl$typeParams $traitText${typeRef.text}".escaped)
    lines += whereClause?.documentationText.orEmpty()
    return lines
}

private val RsTraitItem.declarationText: List<String> get() {
    val name = presentableQualifiedName ?: return emptyList()
    val typeParams = typeParameterList?.text?.escaped ?: ""
    val lines = mutableListOf("$name$typeParams")
    lines += whereClause?.documentationText.orEmpty()
    return lines
}

private val RsTypeBearingItemElement.declarationModifiers: List<String> get() {
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

private val RsWhereClause.documentationText: List<String> get() =
    listOf("where") + wherePredList.map { "&nbsp;&nbsp;&nbsp;&nbsp;${it.text.escaped}," }

private val RsDocAndAttributeOwner.presentableQualifiedModName: String? get() =
    presentableQualifiedName?.removeSuffix("::$name")

private inline fun pre(block: () -> String?): String? = block()?.let { "<pre>$it</pre>" }
