/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.*
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.annotations.Nls
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.psi.RsDocComment
import org.rust.lang.doc.psi.RsDocKind
import java.net.URI

fun RsDocAndAttributeOwner.documentation(withInner: Boolean = true): String =
    docElements(withInner)
        .mapNotNull {
            when (it) {
                is RsAttr -> it.docAttr?.let { text -> RsDocKind.Attr to text }
                is RsDocComment -> RsDocKind.of(it.tokenType) to it.text
                else -> null
            }
        }
        .flatMap { (kind, text) -> kind.removeDecoration(text) }
        .joinToString("\n")

fun RsDocAndAttributeOwner.documentationAsHtml(
    originalElement: PsiElement = this,
    renderMode: RsDocRenderMode = RsDocRenderMode.QUICK_DOC_POPUP
): String? {
    return documentationAsHtml(documentation(), originalElement, renderMode)
}

@Nls
fun RsDocComment.documentationAsHtml(renderMode: RsDocRenderMode = RsDocRenderMode.QUICK_DOC_POPUP): String? {
    val owner = owner ?: return null
    val documentationText = RsDocKind.of(tokenType)
        .removeDecoration(text)
        .joinToString("\n")

    return documentationAsHtml(documentationText, owner, renderMode)
}

@NlsSafe
private fun documentationAsHtml(
    rawDocumentationText: String,
    context: PsiElement,
    renderMode: RsDocRenderMode
): String? {
    // We need some host with unique scheme to
    //
    // 1. make `URI#resolve` work properly somewhere in markdown to html converter implementation
    // 2. identify relative links to language items from other links
    //
    // We can't use `DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL` scheme here
    // because it contains `_` and it is invalid symbol for URI scheme
    val tmpUriPrefix = "psi://element/"
    val path = when {
        context is RsQualifiedNamedElement -> RsQualifiedName.from(context)?.toUrlPath()
        // documentation generation for primitive types via the corresponding module
        context is RsPath -> if (TyPrimitive.fromPath(context) != null) "$STD/" else return null
        // documentation generation for keywords
        context.isKeywordLike() -> "$STD/"
        else -> return null
    }
    val baseURI = if (path != null) {
        try {
            URI.create("$tmpUriPrefix$path")
        } catch (e: Exception) {
            null
        }
    } else null

    val flavour = RustDocMarkdownFlavourDescriptor(context, baseURI, renderMode)
    val root = MarkdownParser(flavour).buildMarkdownTreeFromString(rawDocumentationText)
    return HtmlGenerator(rawDocumentationText, root, flavour).generateHtml()
        .replace(tmpUriPrefix, DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
}

/**
 * Returns elements contribute to documentation of the element
 */
fun RsDocAndAttributeOwner.docElements(withInner: Boolean = true): Sequence<PsiElement> {
    // rustdoc appends the contents of each doc comment and doc attribute in order
    // so we have to resolve these attributes that are edge-bound at the top of the
    // children list.
    val outerDocs = childrenWithLeaves
        // All these outer elements have been edge bound; if we reach something that isn't one
        // of these, we have reached the actual parse children of this item.
        .takeWhile { it is RsOuterAttr || it is PsiComment || it is PsiWhiteSpace }
        .filter { it is RsOuterAttr && it.isDocAttr || it is RsDocComment && it.tokenType in RS_OUTER_DOC_COMMENTS }
    val innerDocs = if (withInner) {
        // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
        // lexical order, after the outer elements. This only applies to functions and modules.
        val childBlock = childOfType<RsBlock>() ?: this
        childBlock.childrenWithLeaves
            .filter { it is RsInnerAttr && it.isDocAttr || it is RsDocComment && it.tokenType in RS_INNER_DOC_COMMENTS }
    } else {
        emptySequence()
    }
    return outerDocs + innerDocs
}

private val RsAttr.isDocAttr: Boolean
    get() = metaItem.name == "doc"

private val RsAttr.docAttr: String?
    get() = if (isDocAttr) metaItem.litExpr?.stringValue else null

private class RustDocMarkdownFlavourDescriptor(
    private val context: PsiElement,
    private val uri: URI? = null,
    private val renderMode: RsDocRenderMode,
    private val gfm: MarkdownFlavourDescriptor = GFMFlavourDescriptor(useSafeLinks = false, absolutizeAnchorLinks = true)
) : MarkdownFlavourDescriptor by gfm {

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
        val generatingProviders = HashMap(gfm.createHtmlGeneratingProviders(linkMap, uri ?: baseURI))
        // Filter out MARKDOWN_FILE to avoid producing unnecessary <body> tags
        generatingProviders.remove(MarkdownElementTypes.MARKDOWN_FILE)
        // h1 and h2 are too large
        generatingProviders[MarkdownElementTypes.ATX_1] = SimpleTagProvider("h2")
        generatingProviders[MarkdownElementTypes.ATX_2] = SimpleTagProvider("h3")
        generatingProviders[MarkdownElementTypes.CODE_FENCE] = RsCodeFenceProvider(context, renderMode)

        generatingProviders[MarkdownElementTypes.SHORT_REFERENCE_LINK] =
            RsReferenceLinksGeneratingProvider(linkMap, uri ?: baseURI, resolveAnchors = true)
        generatingProviders[MarkdownElementTypes.FULL_REFERENCE_LINK] =
            RsReferenceLinksGeneratingProvider(linkMap, uri ?: baseURI, resolveAnchors = true)
        generatingProviders[MarkdownElementTypes.INLINE_LINK] =
            RsInlineLinkGeneratingProvider(uri ?: baseURI, resolveAnchors = true)

        return generatingProviders
    }
}

// Inspired by org.intellij.markdown.html.CodeFenceGeneratingProvider
private class RsCodeFenceProvider(
    private val context: PsiElement,
    private val renderMode: RsDocRenderMode
) : GeneratingProvider {

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

        loop@ for (child in childrenToConsider) {
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
                val trimmedLine = rawLine.trimStart()
                val codeLine = when {
                    // `#` or `# `
                    trimmedLine.startsWith("#") && trimmedLine.getOrNull(1)?.equals(' ') != false -> {
                        skipNextEOL = true
                        continue@loop
                    }
                    trimmedLine.startsWith("##") -> trimmedLine.removePrefix("#")
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
            codeText.appendLine()
        }

        visitor.consumeHtml(convertToHtmlWithHighlighting(codeText.toString()))
    }

    private fun convertToHtmlWithHighlighting(codeText: String): String {
        var htmlCodeText = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(context, codeText)

        // TODO: use scheme of concrete editor instead of global one because they may differ
        val scheme = EditorColorsManager.getInstance().globalScheme
        htmlCodeText = htmlCodeText.replaceFirst("<pre>",
            "<pre style=\"text-indent: ${CODE_SNIPPET_INDENT}px;\">")

        return when (renderMode) {
            RsDocRenderMode.INLINE_DOC_COMMENT -> htmlCodeText.dimColors(scheme)
            else -> htmlCodeText
        }
    }

    private fun String.dimColors(scheme: EditorColorsScheme): String {
        val alpha = if (isColorSchemeDark(scheme)) DARK_THEME_ALPHA else LIGHT_THEME_ALPHA

        return replace(COLOR_PATTERN) { result ->
            val colorHexValue = result.groupValues[1]
            val fgColor = ColorHexUtil.fromHexOrNull(colorHexValue) ?: return@replace result.value
            val bgColor = scheme.defaultBackground
            val finalColor = ColorUtil.mix(bgColor, fgColor, alpha)

            "color: #${ColorUtil.toHex(finalColor)}"
        }
    }

    private fun isColorSchemeDark(scheme: EditorColorsScheme): Boolean {
        return ColorUtil.isDark(scheme.defaultBackground)
    }

    companion object {
        private val COLOR_PATTERN = """color:\s*#(\p{XDigit}{3,})""".toRegex()

        private const val CODE_SNIPPET_INDENT = 20
        private const val LIGHT_THEME_ALPHA = 0.6
        private const val DARK_THEME_ALPHA = 0.78
    }
}

enum class RsDocRenderMode {
    QUICK_DOC_POPUP,
    INLINE_DOC_COMMENT
}

private fun linkIsProbablyValidRustPath(link: CharSequence): Boolean {
    return link.none { it in "/.#" || it.isWhitespace() }
}

private fun markLinkAsLanguageItemIfItIsRustPath(link: CharSequence): CharSequence {
    return if (linkIsProbablyValidRustPath(link)) "${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$link" else link
}

open class RsReferenceLinksGeneratingProvider(private val linkMap: LinkMap, baseURI: URI?, resolveAnchors: Boolean)
    : ReferenceLinksGeneratingProvider(linkMap, baseURI, resolveAnchors) {
    override fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
        super.renderLink(visitor, text, node, info.copy(destination = markLinkAsLanguageItemIfItIsRustPath(info.destination)))
    }

    override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
        val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return null
        val labelText = label.getTextInNode(text)

        val linkInfo = linkMap.getLinkInfo(labelText)
        val (linkDestination, linkTitle) = if (linkInfo != null) {
            linkInfo.destination to linkInfo.title
        } else {
            // then maybe it's implied shortcut reference link, i.e. shortcut reference link without a matching link reference definition
            // (see https://rust-lang.github.io/rfcs/1946-intra-rustdoc-links.html#implied-shortcut-reference-links)
            // so "[Iterator]" is the same as "[Iterator](Iterator)" and will be eventually rendered as "<a href="psi_element://Iterator">Iterator</a>"
            val linkText = labelText.removeSurrounding("[", "]").removeSurrounding("`")
            if (!linkIsProbablyValidRustPath(linkText)) return null
            linkText to null
        }

        val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        return RenderInfo(
            linkTextNode ?: label,
            EntityConverter.replaceEntities(linkDestination, processEntities = true, processEscapes = true),
            linkTitle?.let { EntityConverter.replaceEntities(it, processEntities = true, processEscapes = true) }
        )
    }
}

open class RsInlineLinkGeneratingProvider(baseURI: URI?, resolveAnchors: Boolean)
    : InlineLinkGeneratingProvider(baseURI, resolveAnchors) {
    override fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
        super.renderLink(visitor, text, node, info.copy(destination = markLinkAsLanguageItemIfItIsRustPath(info.destination)))
    }
}
