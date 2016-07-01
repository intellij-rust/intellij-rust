package org.rust.lang.doc

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.MarkdownUtil
import com.petebevin.markdown.MarkdownProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.util.stringLiteralValue
import org.rust.lang.doc.psi.RustDocKind

fun RustDocAndAttributeOwner.documentation(): String? =
    (outerDocs() + innerDocs())
        .map { it.first to it.second.splitToSequence("\r\n", "\r", "\n") }
        .flatMap { it.first.removeDecoration(it.second) }
        .joinToString("\n")

fun RustDocAndAttributeOwner.documentationAsHtml(): String? {
    val raw = documentation() ?: return null
    val lines = raw.split("\n").toMutableList()
    MarkdownUtil.replaceHeaders(lines)
    MarkdownUtil.replaceCodeBlock(lines)
    return MarkdownProcessor().markdown(lines.joinToString("\n"))
}

private fun RustDocAndAttributeOwner.outerDocs(): Sequence<Pair<RustDocKind, String>> {
    // rustdoc appends the contents of each doc comment and doc attribute in order
    // so we have to resolve these attributes that are edge-bound at the top of the
    // children list.
    val childOuterIterator = PsiTreeUtil.childIterator(this, PsiElement::class.java)
    return childOuterIterator.asSequence()
        // All these outer elements have been edge bound; if we reach something that isn't one
        // of these, we have reached the actual parse children of this item.
        .takeWhile { it is RustOuterAttrElement || it is PsiComment || it is PsiWhiteSpace }
        .mapNotNull {
            when {
                it is RustOuterAttrElement -> it.metaItem.docAttr?.let { RustDocKind.Attr to it }
                it is PsiComment && (it.tokenType == OUTER_EOL_DOC_COMMENT
                    || it.tokenType == OUTER_BLOCK_DOC_COMMENT) -> RustDocKind.of(it.tokenType) to it.text
                else -> null
            }
        }
}

private fun RustDocAndAttributeOwner.innerDocs(): Sequence<Pair<RustDocKind, String>> {
    // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
    // lexical order, after the outer elements. This only applies to functions and modules.
    val childBlock = PsiTreeUtil.findChildOfType(this, RustBlockElement::class.java) ?: this

    val childInnerIterator = PsiTreeUtil.childIterator(childBlock, PsiElement::class.java)
    childInnerIterator.next() // skip the first open bracket ...
    return childInnerIterator.asSequence()
        // We only consider comments and attributes at the beginning.
        // Technically, anything else is a syntax error.
        .takeWhile { it is RustInnerAttrElement || it is PsiComment || it is PsiWhiteSpace }
        .mapNotNull {
            when {
                it is RustInnerAttrElement -> it.metaItem.docAttr?.let { RustDocKind.Attr to it }
                it is PsiComment && (it.tokenType == INNER_EOL_DOC_COMMENT
                    || it.tokenType == INNER_BLOCK_DOC_COMMENT) -> RustDocKind.of(it.tokenType) to it.text
                else -> null
            }
        }
}

private val RustMetaItemElement.docAttr: String?
    get() = if (identifier.text == "doc") litExpr?.stringLiteralValue else null
