package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.doc.documentationAsHtml

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element !is RustDocAndAttributeOwner) return null

        val name = if (element is RustMod) element.modName else element.name
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + doc
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = when (element) {
        is RustPatBindingElement -> getQuickNavigateInfo(element)
        is RustFnElement         -> getQuickNavigateInfo(element)
        else                     -> null
    }

    private fun getQuickNavigateInfo(element: RustPatBindingElement): String {
        val location = element.locationString
        val bindingMode = if (element.isMut) "mut " else ""

        return "let $bindingMode<b>${element.identifier.text}</b>$location"
    }

    private fun getQuickNavigateInfo(element: RustFnElement): String {
        val signature = element.formatSignature()
        val location = element.locationString
        return "$signature$location"
    }

    private fun RustFnElement.formatSignature(): String {
        // fn item looks like this:
        // ```
        //     ///doc comment
        //     #[attribute]
        //     pub const unsafe extern "C" fn foo<T>(x: T): where T: Clone { ... }
        // ```
        //
        // we want to show only the signature, and make the name bold

        var signatureStartElement = firstChild
        loop@while (true) {
            when (signatureStartElement) {
                is PsiWhiteSpace, is PsiComment, is RustOuterAttrElement -> {
                    signatureStartElement = signatureStartElement.nextSibling
                }
                else -> break@loop
            }
        }

        val signatureStart = signatureStartElement?.startOffsetInParent ?: 0

        // pick (in order) where clause, return type, or closing brace of the parameters
        // if all else fails, drop down to the length of the current element
        val functionElements = listOf(whereClause, retType, parameters)
        val signatureEnd = functionElements
                .filterNotNull().firstOrNull()
                ?.let { it.startOffsetInParent + it.textLength }
                ?: textLength

        val identStart = identifier.startOffsetInParent
        val identEnd = identStart + identifier.textLength
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

    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()

    private val String.escaped: String get() = StringUtil.escapeXml(this)
}
