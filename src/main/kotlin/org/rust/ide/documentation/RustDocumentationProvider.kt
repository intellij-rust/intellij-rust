package org.rust.ide.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.util.text.MarkdownUtil
import com.petebevin.markdown.MarkdownProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustImplMethodMemberImpl
import org.rust.lang.core.psi.impl.mixin.isMut

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        when (element) {
            is RustItem -> {
                val doc = element.documentation
                return if (doc != null) formatDoc(element.name!!, doc)
                else null
            }
            // THIS IS A HACK UNTIL RustImplMethodMember IS A RustItem
            // I have a hunch fixing that will also fix struct member resolution...
            is RustImplMethodMemberImpl -> {
                val doc = element.documentation
                return if (doc != null) formatDoc(element.name!!, doc)
                else null
            }
            else -> {
                return null
            }
        }
    }

    private fun formatDoc(name: String, docString: String): String {
        val lines = docString.split("\n").toMutableList()
        MarkdownUtil.replaceHeaders(lines)
        MarkdownUtil.replaceCodeBlock(lines)
        val mdp = MarkdownProcessor()
        val md = mdp.markdown(lines.joinToString("\n"))
        return "<pre>$name</pre>\n$md"
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?) = when (element) {
        is RustPatBinding -> getQuickNavigateInfo(element)
        is RustFnItem     -> getQuickNavigateInfo(element)
        else              -> null
    }

    private fun getQuickNavigateInfo(element: RustPatBinding): String {
        val location = element.locationString
        val bindingMode = if (element.isMut) "mut " else ""

        return "let $bindingMode<b>${element.identifier.text}</b>$location"
    }

    private fun getQuickNavigateInfo(element: RustFnItem): String {
        val signature = element.formatSignature()
        val location = element.locationString
        return "$signature$location"
    }

    private fun RustFnItem.formatSignature(): String {
        val identStart = identifier.startOffsetInParent
        val identEnd = identStart + identifier.textLength
        val signatureLength = block?.startOffsetInParent ?: textLength

        val beforeIdent = text.subSequence(0, identStart)
        val identText = text.subSequence(identStart, identEnd)
        val afterIdent = text.subSequence(identEnd, signatureLength).toString().trimEnd()

        return "$beforeIdent<b>$identText</b>$afterIdent"
    }

    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()
}
