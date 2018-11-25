/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocKind
import java.net.URI

fun RsDocAndAttributeOwner.documentation(): String? =
    (outerDocs() + innerDocs())
        .map { it.first to it.second.splitToSequence("\r\n", "\r", "\n") }
        .flatMap { it.first.removeDecoration(it.second) }
        .joinToString("\n")

fun RsDocAndAttributeOwner.documentationAsHtml(): String? {
    // We need some host with unique scheme to
    //
    // 1. make `URI#resolve` work properly somewhere in markdown to html converter implementation
    // 2. identify relative links to language items from other links
    //
    // We can't use `DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL` scheme here
    // because it contains `_` and it is invalid symbol for URI scheme
    val tmpUriPrefix = "psi://element/"
    val baseURI = if (this is RsQualifiedNamedElement) {
        val path = RsQualifiedName.from(this)?.toUrlPath()
        if (path != null) {
            try {
                URI.create("$tmpUriPrefix$path")
            } catch (e: Exception) {
                null
            }
        } else null
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
    val childOuterIterable = SyntaxTraverser.psiTraverser().children(this)
    return childOuterIterable.asSequence()
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
    val childBlock = PsiTreeUtil.getChildOfType(this, RsBlock::class.java) ?: this

    val childInnerIterable = SyntaxTraverser.psiTraverser().children(childBlock)

    return childInnerIterable.asSequence()
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
    get() = if (name == "doc") litExpr?.stringLiteralValue else null

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
        // BACKCOMPAT: 2018.2
        val codeFenceProvider = createCodeFenceProvider(context)
        if (codeFenceProvider != null) {
            generatingProviders[MarkdownElementTypes.CODE_FENCE] = codeFenceProvider
        }
        return generatingProviders
    }
}
