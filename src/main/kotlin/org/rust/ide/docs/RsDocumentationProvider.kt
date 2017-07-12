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
        is RsFunction -> element.header(false) + element.signature(false)
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
            FREE, FOREIGN -> listOfNotNull(presentableQualifiedName?.removeSuffix("::$name"))
            IMPL_METHOD -> {
                val mod = presentableQualifiedName?.removeSuffix("::$name")
                if (mod != null) {
                    val lines = mutableListOf(mod)
                    val parent = parent as? RsImplItem
                    val typeRef = parent?.typeReference
                    if (typeRef != null) {
                        val traitRef = parent.traitRef
                        val traitText = if (traitRef != null) "${traitRef.text} for " else ""
                        val typeParams = parent.typeParameterList?.text ?: ""
                        lines += "impl$typeParams $traitText${typeRef.text}".escaped
                        lines += parent.whereClause?.documentationText.orEmpty()
                    }
                    lines
                } else emptyList()
            }
            TRAIT_METHOD -> {
                val parent = parent as? RsTraitItem
                val name = parent?.presentableQualifiedName
                if (name != null) {
                    val typeParams = parent.typeParameterList?.text?.escaped ?: ""
                    val lines = mutableListOf("$name$typeParams")
                    lines += parent.whereClause?.documentationText.orEmpty()
                    lines
                } else emptyList()
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

private val RsWhereClause.documentationText: List<String> get() =
    listOf("where") + wherePredList.map { "&nbsp;&nbsp;&nbsp;&nbsp;${it.text.escaped}," }

private fun RsDocAndAttributeOwner.signature(usePreTag: Boolean): String {
    val rawSignature = when (this) {
        is RsFunction -> {
            val shortSignature = presentationInfo?.shortSignatureText
            if (shortSignature != null) {
                val signatureParts = mutableListOf<String>()
                if (isPublic) {
                    signatureParts += "pub"
                }
                if (isConst) {
                    signatureParts += "const"
                }
                if (isUnsafe) {
                    signatureParts += "unsafe"
                }
                if (isExtern) {
                    signatureParts += "extern"
                    abiName?.let { signatureParts += it }
                }
                signatureParts += "fn"
                signatureParts += shortSignature
                signatureParts.joinToString(" ")
            } else null
        }
        is RsFieldDecl -> presentationInfo?.signatureText
        else -> null
    }
    val startTag = if (usePreTag) "<pre>" else ""
    val endTag = if (usePreTag) "</pre>" else ""
    return if (rawSignature != null) "$startTag$rawSignature$endTag\n" else ""
}

private inline fun pre(block: () -> String?): String? = block()?.let { "<pre>$it</pre>" }
