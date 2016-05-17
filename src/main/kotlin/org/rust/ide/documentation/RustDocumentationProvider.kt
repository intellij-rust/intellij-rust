package org.rust.ide.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.MarkdownUtil
import com.petebevin.markdown.MarkdownProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isMut

class RustDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is RustItem) {
            return element.documentation?.let { formatDoc(element.name ?: "", it) }
        }
        return null
    }

    private fun formatDoc(name: String, docString: String): String {
        val lines = docString.split("\n").toMutableList()
        MarkdownUtil.replaceHeaders(lines)
        MarkdownUtil.replaceCodeBlock(lines)
        val mdp = MarkdownProcessor()
        val md = mdp.markdown(lines.joinToString("\n"))
        return "<pre>$name</pre>\n$md"
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = when (element) {
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

val RustItem.documentation: String?
    get() {
        return (outerDocumentationLinesForElement +
            innerDocumentationLinesForElement).joinToString("\n")
    }


private val RustItem.outerDocumentationLinesForElement: List<String>
    get() {
        // rustdoc appends the contents of each doc comment and doc attribute in order
        // so we have to resolve these attributes that are edge-bound at the top of the
        // children list.
        val childOuterIterator = PsiTreeUtil.childIterator(this, PsiElement::class.java)
        return childOuterIterator.asSequence()
            // All these outer elements have been edge bound; if we reach something that isn't one
            // of these, we have reached the actual parse children of this item.
            .takeWhile { it is RustOuterAttr || it is PsiComment || it is PsiWhiteSpace }
            .mapNotNull {
                when {
                    it is RustOuterAttr && it.metaItem.identifier.textMatches("doc") ->
                        it.metaItem.litExpr?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                    it is PsiComment && it.tokenType == RustTokenElementTypes.OUTER_DOC_COMMENT ->
                        it.text.substringAfter("///").trim()
                    else -> null
                }
            }.toList()
    }

private val RustItem.innerDocumentationLinesForElement: List<String>
    get() {
        // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
        // lexical order, after the outer elements. This only applies to functions and modules.
        val childBlock = PsiTreeUtil.findChildOfType(this, RustBlock::class.java)
            ?: return emptyList()

        val childInnerIterator = PsiTreeUtil.childIterator(childBlock, PsiElement::class.java)
        childInnerIterator.next() // skip the first open bracket ...
        return childInnerIterator.asSequence()
            // We only consider comments and attributes at the beginning.
            // Technically, anything else is a syntax error.
            .takeWhile { it is RustInnerAttr || it is PsiComment || it is PsiWhiteSpace }
            .mapNotNull {
                when {
                    it is RustInnerAttr && it.metaItem.identifier.textMatches("doc") ->
                        it.metaItem.litExpr?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                    it is PsiComment && it.tokenType == RustTokenElementTypes.INNER_DOC_COMMENT ->
                        it.text.substringAfter("//!").trim()
                    else -> null
                }
            }.toList()
    }
