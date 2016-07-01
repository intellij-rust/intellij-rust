package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustDocAndAttributeOwner
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.doc.documentationAsHtml

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? =
        if (element is RustDocAndAttributeOwner) {
            element.documentationAsHtml().let {
                if (element.name != null) {
                    "<pre>${element.name}</pre>\n$it"
                } else {
                    it
                }
            }
        } else {
            null
        }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = when (element) {
        is RustPatBindingElement -> getQuickNavigateInfo(element)
        is RustFnItemElement     -> getQuickNavigateInfo(element)
        else                     -> null
    }

    private fun getQuickNavigateInfo(element: RustPatBindingElement): String {
        val location = element.locationString
        val bindingMode = if (element.isMut) "mut " else ""

        return "let $bindingMode<b>${element.identifier.text}</b>$location"
    }

    private fun getQuickNavigateInfo(element: RustFnItemElement): String {
        val signature = element.formatSignature()
        val location = element.locationString
        return "$signature$location"
    }

    private fun RustFnItemElement.formatSignature(): String {
        // fn item looks like this:
        // ```
        //     ///doc comment
        //     #[attribute]
        //     pub const unsafe extern "C" fn foo<T>(x: T): where T: Clone { ... }
        // ```
        //
        // we want to show only the signature, and make the name bold
        val signatureStartElement = listOf(vis, const, unsafe, externAbi, fn).filterNotNull().firstOrNull()
        val sigtatureStart = signatureStartElement?.startOffsetInParent ?: 0
        val signatureEnd = block?.startOffsetInParent ?: textLength

        val identStart = identifier.startOffsetInParent
        val identEnd = identStart + identifier.textLength

        val beforeIdent = text.subSequence(sigtatureStart, identStart)
        val afterIdent = text.subSequence(identEnd, signatureEnd).toString().trimEnd()

        return "$beforeIdent<b>$name</b>$afterIdent"
    }

    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()
}
