package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element !is RsDocAndAttributeOwner) return null

        val name = if (element is RsMod) element.modName else element.name
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val functionSignature = (element as? RsFunction)?.formatSignature()
        val signature = if (functionSignature != null) "<pre>$functionSignature</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + signature + doc
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = when (element) {
        is RsPatBinding -> getQuickNavigateInfo(element)
        is RsNamedElement -> element.getQuickNavigateInfo()
        else -> null
    }

    private fun getQuickNavigateInfo(element: RsPatBinding): String {
        val location = element.locationString
        val bindingMode = if (element.isMut) "mut " else ""
        val prefix = if (element.parentOfType<RsValueParameter>() != null) "" else "let "
        return  "$prefix$bindingMode<b>${element.identifier.text}</b>$location"
    }

    private fun RsNamedElement.formatSignature(): String {
        // Example:
        // fn item looks like this:
        // ```
        //     ///doc comment
        //     #[attribute]
        //     pub const unsafe extern "C" fn foo<T>(x: T): where T: Clone { ... }
        // ```
        //
        // we want to show only the signature, and make the name bold

        var signatureStartElement = firstChild
        loop@ while (true) {
            when (signatureStartElement) {
                is PsiWhiteSpace, is PsiComment, is RsOuterAttr -> {
                    signatureStartElement = signatureStartElement.nextSibling
                }
                else -> break@loop
            }
        }

        val signatureStart = signatureStartElement?.startOffsetInParent ?: 0
        val stopAt = when (this) {
            is RsFunction -> listOf(whereClause, retType, valueParameterList)
            is RsStructItem -> if (blockFields != null) listOf(whereClause) else listOf(whereClause, tupleFields)
            is RsEnumItem -> listOf(whereClause)
            is RsEnumVariant -> listOf(tupleFields)
            is RsTraitItem -> listOf(whereClause)
            is RsTypeAlias -> listOf(typeReference, typeParamBounds, whereClause)
            is RsConstant -> listOf(expr, typeReference)
            is RsSelfParameter -> listOf(typeReference)
            else -> listOf(navigationElement)
        }

        // pick (in order) elements we should stop at
        // if they all fail, drop down to the end of the id element
        val idElement = if (this is RsSelfParameter) self else navigationElement
        val signatureEnd = stopAt
                .filterNotNull().firstOrNull()
                ?.let { it.startOffsetInParent + it.textLength }
                ?: idElement.startOffsetInParent + idElement.textLength

        val identStart = idElement.startOffsetInParent
        val identEnd = identStart + idElement.textLength
        check(signatureStart <= identStart && identStart <= identEnd &&
            identEnd <= signatureEnd && signatureEnd <= textLength)

        val beforeIdent = text.substring(signatureStart, identStart).escaped
        val afterIdent = text.substring(identEnd, signatureEnd)
            .replace("""\s+""".toRegex(), " ")
            .replace("( ", "(")
            .replace(" )", ")")
            .replace(" ,", ",")
            .trimEnd()
            .escaped

        return "$beforeIdent<b>$name</b>$afterIdent"
    }

    private fun RsNamedElement.getQuickNavigateInfo(): String =
        "${formatSignature()}$locationString"

    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()

    private val String.escaped: String get() = StringUtil.escapeXml(this)
}
