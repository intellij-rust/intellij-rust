package org.rust.ide.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.util.text.MarkdownUtil
import com.petebevin.markdown.MarkdownProcessor
import org.rust.lang.core.psi.RustDocAndAttributeOwner
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.documentation
import org.rust.lang.core.psi.impl.mixin.isMut

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is RustDocAndAttributeOwner) {
            return element.documentation?.let { formatDoc(element.name ?: "", it) }
        }
        return null
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = when (element) {
        is RustPatBindingElement -> getQuickNavigateInfo(element)
        is RustFnItemElement     -> getQuickNavigateInfo(element)
        else              -> null
    }

    private fun formatDoc(name: String, docString: String): String {
        val lines = docString.split("\n").toMutableList()
        MarkdownUtil.replaceHeaders(lines)
        MarkdownUtil.replaceCodeBlock(lines)
        val mdp = MarkdownProcessor()
        val md = mdp.markdown(lines.joinToString("\n"))
        return "<pre>$name</pre>\n$md"
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
