/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.psi.RsDocKind
import java.net.URI

fun RsDocAndAttributeOwner.documentation(): String? =
    (outerDocs() + innerDocs())
        .map { it.first to it.second.splitToSequence("\r\n", "\r", "\n") }
        .flatMap { it.first.removeDecoration(it.second) }
        .joinToString("\n")

fun RsDocAndAttributeOwner.documentationAsHtml(originalElement: RsElement = this): String? {
    // We need some host with unique scheme to
    //
    // 1. make `URI#resolve` work properly somewhere in markdown to html converter implementation
    // 2. identify relative links to language items from other links
    //
    // We can't use `DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL` scheme here
    // because it contains `_` and it is invalid symbol for URI scheme
    val tmpUriPrefix = "psi://element/"
    val path = when (originalElement) {
        is RsQualifiedNamedElement -> RsQualifiedName.from(originalElement)?.toUrlPath()
        // generating documentation for primitive types via the corresponding module
        is RsPath -> if (TyPrimitive.fromPath(originalElement) != null) "$STD/" else return null
        else -> return null
    }
    val baseURI = if (path != null) {
        try {
            URI.create("$tmpUriPrefix$path")
        } catch (e: Exception) {
            null
        }
    } else null

    val text = documentation() ?: return null
    val flavour = RustDocMarkdownFlavourDescriptor(this, baseURI)
    val root = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
    return HtmlGenerator(text, root, flavour).generateHtml()
        .replace(tmpUriPrefix, DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
}

private fun RsDocAndAttributeOwner.outerDocs(): Sequence<Pair<RsDocKind, String>> {
    // rustdoc appends the contents of each doc comment and doc attribute in order
    // so we have to resolve these attributes that are edge-bound at the top of the
    // children list.
    return childrenWithLeaves
        // All these outer elements have been edge bound; if we reach something that isn't one
        // of these, we have reached the actual parse children of this item.
        .takeWhile { it is RsOuterAttr || it is PsiComment || it is PsiWhiteSpace }
        .mapNotNull {
            when {
                it is RsOuterAttr -> it.metaItem.docAttr?.let { RsDocKind.Attr to it }
                it is PsiComment && (it.tokenType == OUTER_EOL_DOC_COMMENT
                    || it.tokenType == OUTER_BLOCK_DOC_COMMENT) -> RsDocKind.of(it.tokenType) to it.text
                else -> null
            }
        }
}

private fun RsDocAndAttributeOwner.innerDocs(): Sequence<Pair<RsDocKind, String>> {
    // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
    // lexical order, after the outer elements. This only applies to functions and modules.
    val childBlock = childOfType<RsBlock>() ?: this

    return childBlock.childrenWithLeaves
        .mapNotNull {
            when {
                it is RsInnerAttr -> it.metaItem.docAttr?.let { RsDocKind.Attr to it }
                it is PsiComment && (it.tokenType == INNER_EOL_DOC_COMMENT
                    || it.tokenType == INNER_BLOCK_DOC_COMMENT) -> RsDocKind.of(it.tokenType) to it.text
                else -> null
            }
        }
}

private val RsMetaItem.docAttr: String?
    get() = if (name == "doc") litExpr?.stringValue else null

private class RustDocMarkdownFlavourDescriptor(
    private val context: PsiElement,
    private val uri: URI? = null,
    private val gfm: MarkdownFlavourDescriptor = GFMFlavourDescriptor()
) : MarkdownFlavourDescriptor by gfm {

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
        val generatingProviders = HashMap(gfm.createHtmlGeneratingProviders(linkMap, uri ?: baseURI))
        // Filter out MARKDOWN_FILE to avoid producing unnecessary <body> tags
        generatingProviders.remove(MarkdownElementTypes.MARKDOWN_FILE)
        // h1 and h2 are too large
        generatingProviders[MarkdownElementTypes.ATX_1] = SimpleTagProvider("h2")
        generatingProviders[MarkdownElementTypes.ATX_2] = SimpleTagProvider("h3")
        generatingProviders[MarkdownElementTypes.CODE_FENCE] = RsCodeFenceProvider(context)
        return generatingProviders
    }
}

// Inspired by org.intellij.markdown.html.CodeFenceGeneratingProvider
private class RsCodeFenceProvider(private val context: PsiElement) : GeneratingProvider {

    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length

        val codeText = StringBuilder()

        var childrenToConsider = node.children
        if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
            childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size - 1)
        }

        var isContentStarted = false
        var skipNextEOL = false
        var lastChildWasContent = false

        loop@for (child in childrenToConsider) {
            if (isContentStarted && child.type in listOf(MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL)) {
                if (skipNextEOL && child.type == MarkdownTokenTypes.EOL) {
                    skipNextEOL = false
                    continue
                }
                val rawLine = HtmlGenerator.trimIndents(child.getTextInNode(text), indentBefore)
                // `cargo doc` has special rules for code lines which start with `#`:
                //   * `# ` prefix is used to mark lines that should be skipped in rendered documentation.
                //   * `##` prefix is converted to `#`
                // See https://github.com/rust-lang/rust/blob/5182cc1ca65d05f16ee5e1529707ac6f63233ca9/src/librustdoc/html/markdown.rs#L114 for more details
                val codeLine = when {
                    rawLine.startsWith("# ") -> {
                        skipNextEOL = true
                        continue@loop
                    }
                    rawLine.startsWith("##") -> rawLine.removePrefix("#")
                    else -> rawLine
                }

                codeText.append(codeLine)
                lastChildWasContent = child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
            }
            if (!isContentStarted && child.type == MarkdownTokenTypes.EOL) {
                isContentStarted = true
            }
        }
        if (lastChildWasContent) {
            codeText.appendln()
        }
        val htmlCodeText = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(context, codeText.toString())
        visitor.consumeHtml(htmlCodeText)
    }
}
