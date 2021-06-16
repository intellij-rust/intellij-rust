/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lang.parser.GeneratedParserUtilBase._NONE_
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.util.CharTable
import org.rust.lang.RsLanguage
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParser
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_LINK_DEFINITION

object RsDocLinkDestinationParser {
    fun parse(text: CharSequence, charTable: CharTable): TreeElement {
        if (text.contains("/")) return docDataLeaf(text, charTable)

        val hashIndex = text.indexOf("#")

        // `#foo`
        if (hashIndex == 0) return docDataLeaf(text, charTable)

        val link = if (hashIndex != -1) text.subSequence(0, hashIndex) else text

        val prefixLen = link.indexOf("@") + 1
        val suffixLen = when {
            link.endsWith("!()") -> 3
            link.endsWith("()") -> 2
            link.endsWith("!") -> 1
            else -> 0
        }

        val pathText = link.subSequence(prefixLen, link.length - suffixLen)
        val path = parseRsPath(pathText, charTable) ?: return docDataLeaf(text, charTable)

        val root = DOC_LINK_DEFINITION.createCompositeNode()
        if (prefixLen > 0) {
            root.rawAddChildrenWithoutNotifications(docDataLeaf(link.subSequence(0, prefixLen), charTable))
        }
        root.rawAddChildrenWithoutNotifications(path)
        if (suffixLen > 0) {
            root.rawAddChildrenWithoutNotifications(docDataLeaf(link.subSequence(link.length - suffixLen, link.length), charTable))
        }
        if (hashIndex != -1) {
            root.rawAddChildrenWithoutNotifications(docDataLeaf(text.substring(hashIndex), charTable))
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

        return builder.treeBuilt.firstChildNode as TreeElement
    }

    private fun docDataLeaf(text: CharSequence, charTable: CharTable) =
        LeafPsiElement(RsDocElementTypes.DOC_DATA, charTable.intern(text))
}
