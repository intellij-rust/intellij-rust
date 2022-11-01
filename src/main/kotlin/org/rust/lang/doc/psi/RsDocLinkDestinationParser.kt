/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lang.parser.GeneratedParserUtilBase._NONE_
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.util.CharTable
import org.rust.lang.RsLanguage
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParser
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_LINK_DEFINITION

/**
 * `fn@mod1::mod2::func#anchor`
 *  ~~~ prefix         ~~~~~~~ suffix
 *     ~~~~~~~~~~~~~~~~ content
 */
private class LinkTextParts private constructor(
    private val text: CharSequence,
    private val startOffset: Int,
    private val endOffset: Int,
) {
    constructor(text: CharSequence) : this(text, startOffset = 0, endOffset = text.length)

    val prefix: CharSequence get() = text.subSequence(0, startOffset)
    val suffix: CharSequence get() = text.subSequence(endOffset, text.length)
    val content: CharSequence get() = text.subSequence(startOffset, endOffset)
    private val contentLength: Int get() = endOffset - startOffset

    fun subSequence(start: Int, end: Int): LinkTextParts {
        if (start == 0 && end == contentLength) return this
        check(start in 0..end && end <= contentLength)
        return LinkTextParts(text, startOffset + start, startOffset + end)
    }

    fun removePrefix(length: Int): LinkTextParts = subSequence(length, contentLength)
    fun removeSuffix(length: Int): LinkTextParts = subSequence(0, contentLength - length)
}

/**
 * See `preprocess_link` in `src/librustdoc/passes/collect_intra_doc_links.rs`
 * Algorithm:
 * - Remove backticks
 * - Remove hash suffix
 * - Remove disambiguator prefix/suffix
 * - Parse path using usual parser (it will parse generics if any)
 */
object RsDocLinkDestinationParser {
    fun parse(text: CharSequence, charTable: CharTable): TreeElement {
        val info = parseLink(text) ?: return docDataLeaf(text, charTable)
        return parsePathAndCreateNodes(info, charTable) ?: docDataLeaf(text, charTable)
    }

    private fun parseLink(text: CharSequence): LinkTextParts? {
        if (text.isEmpty() || text.contains("/")) return null

        return LinkTextParts(text)
            .trimBackticks()
            ?.removeHashAnchor()
            ?.removeDisambiguator()
            ?.takeIf { canBeCorrectLink(it.content) }
    }

    // "`func`" -> "func"
    // Remove any number of backticks at the beginning and end.
    // Rustdoc also removes backticks in the middle, but we don't support it
    private fun LinkTextParts.trimBackticks(): LinkTextParts? {
        val content = content
        var start = 0
        var end = content.length
        while (content[start] == '`') {
            ++start
        }
        if (start == end) return null
        while (content[end - 1] == '`') {
            --end
        }
        return subSequence(start, end)
    }

    // "mod1::mod2#anchor" -> "mod1::mod2"
    private fun LinkTextParts.removeHashAnchor(): LinkTextParts? {
        val parts = content.split('#')
        if (parts.size > 2) return null  // multiple #'s - invalid link
        if (parts.size < 2) return this  // no anchors

        val link = parts[0]
        val hash = parts[1]

        // anchor to an element of the current page - ignore
        if (link.isBlank()) return null

        return removeSuffix("#".length + hash.length)
    }

    // "fn@func" -> "func"
    // "gen!()" -> "gen"
    private fun LinkTextParts.removeDisambiguator(): LinkTextParts? {
        val index = content.indexOf('@')
        if (index != -1) {
            val prefix = content.substring(0, index)
            if (prefix !in KNOWN_PREFIXES) return null
            return removePrefix(prefix.length + "@".length)
                .takeIf { it.content.isNotBlank() }
        } else {
            for (suffix in KNOWN_SUFFIXES) {
                if (content.endsWith(suffix) && content.length > suffix.length) {
                    return removeSuffix(suffix.length)
                }
            }
        }
        return this
    }

    private val KNOWN_PREFIXES: Set<String> = hashSetOf(
        "struct", "enum", "trait", "union", "module", "mod", "const", "constant", "static",
        "function", "fn", "method", "derive", "type", "value", "macro", "prim", "primitive"
    )
    private val KNOWN_SUFFIXES: Array<String> = arrayOf("!()", "!{}", "![]", "()", "!")

    private fun canBeCorrectLink(link: CharSequence): Boolean =
        link.all { it.isAlphanumeric() || it in ":_<>, !*&;" }

    private fun Char.isAlphanumeric(): Boolean = Character.isAlphabetic(code) || isDigit()

    private fun parsePathAndCreateNodes(info: LinkTextParts, charTable: CharTable): TreeElement? {
        val path = parseRsPath(info.content, charTable) ?: return null
        val root = DOC_LINK_DEFINITION.createCompositeNode()
        val prefix = info.prefix
        val suffix = info.suffix
        if (prefix.isNotEmpty()) {
            root.rawAddChildrenWithoutNotifications(docDataLeaf(prefix, charTable))
        }
        root.rawAddChildrenWithoutNotifications(path)
        if (suffix.isNotEmpty()) {
            root.rawAddChildrenWithoutNotifications(docDataLeaf(suffix, charTable))
        }
        return root.firstChildNode
    }

    private fun parseRsPath(pathText: CharSequence, charTable: CharTable): TreeElement? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RsLanguage)
            ?: error("No parser definition for language $RsLanguage")
        val plainBuilder = PsiBuilderImpl(null, null, parserDefinition, RsLexer(), charTable, pathText, null, null)

        val builder = GeneratedParserUtilBase.adapt_builder_(
            DOC_LINK_DEFINITION,
            plainBuilder,
            RustParser(),
            RustParser.EXTENDS_SETS_
        )

        val rootMarker = GeneratedParserUtilBase.enter_section_(builder, 0, _NONE_, null)

        if (!RustParser.TypePathGenericArgs(builder, 0)) {
            return null
        }

        GeneratedParserUtilBase.exit_section_(builder, 0, rootMarker, DOC_LINK_DEFINITION, true, true, GeneratedParserUtilBase.TRUE_CONDITION)

        val treeBuilt = builder.treeBuilt
        if (treeBuilt.findChildByType(TokenType.ERROR_ELEMENT) != null) return null
        return treeBuilt.firstChildNode as TreeElement
    }

    private fun docDataLeaf(text: CharSequence, charTable: CharTable) =
        LeafPsiElement(RsDocElementTypes.DOC_DATA, charTable.intern(text))
}
