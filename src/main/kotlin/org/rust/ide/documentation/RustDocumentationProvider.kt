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
import java.util.*

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
        val lines: List<String> = ArrayList<String>().apply {
            for (c in childOuterIterator) {
                if (c !is RustOuterAttr && c !is PsiComment && c !is PsiWhiteSpace) {
                    // All these outer elements have been edge bound; if we reach something that isn't one
                    // of these, we have reached the actual parse children of this item.
                    break
                } else if (c is RustOuterAttr && c.metaItem.identifier.textMatches("doc")) {
                    val s = (c.metaItem.litExpr)?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                    if (s != null) add(s)
                } else if (c is PsiComment && c.tokenType == RustTokenElementTypes.OUTER_DOC_COMMENT) {
                    val s = c.text.substringAfter("///").trim()
                    add(s)
                }
            }
        }

        return lines
    }

private val RustItem.innerDocumentationLinesForElement: List<String>
    get() {
        // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
        // lexical order, after the outer elements. This only applies to functions and modules.
        val lines: MutableList<String> = ArrayList()

        val childBlock = PsiTreeUtil.findChildOfType(this, RustBlock::class.java)

        if (childBlock != null) {
            val childInnerIterator = PsiTreeUtil.childIterator(childBlock, PsiElement::class.java)
            childInnerIterator.next() // skip the first open bracket ...
            lines.apply {
                for (c in childInnerIterator) {
                    // We only consider comments and attributes at the beginning.
                    // Technically, anything else is a syntax error.
                    if (c !is RustInnerAttr && c !is PsiComment && c !is PsiWhiteSpace) {
                        break
                    } else if (c is RustInnerAttr && c.metaItem.identifier.textMatches("doc")) {
                        val s = (c.metaItem.litExpr)?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                        if (s != null) add(s)
                    } else if (c is PsiComment && c.tokenType == RustTokenElementTypes.INNER_DOC_COMMENT) {
                        val s = c.text.substringAfter("//!").trim()
                        add(s)
                    }
                }
            }
        }

        return lines
    }
