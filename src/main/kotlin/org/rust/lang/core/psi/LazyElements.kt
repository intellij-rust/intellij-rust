/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.tree.IReparseableElementType
import org.rust.lang.RsLanguage
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParserUtil

fun factory(name: String): IElementType = when (name) {
    "MACRO_ARGUMENT" -> RsMacroArgumentElementType
    "MACRO_BODY" -> RsMacroBodyElementType
    else -> error("Unknown element $name")
}

private object RsMacroArgumentElementType : ILazyParseableElementType("MACRO_ARGUMENT", RsLanguage)
private object RsMacroBodyElementType : RsTTBodyLazyElementTypeBase("MACRO_BODY")

private abstract class RsTTBodyLazyElementTypeBase(debugName: String) : RsReparseableElementTypeBase(debugName) {
    override fun isParsable(seq: CharSequence, fileLanguage: Language, project: Project): Boolean =
        RustParserUtil.hasProperTokenTreeBraceBalance(seq, RsLexer())
}

private abstract class RsReparseableElementTypeBase(debugName: String) : IReparseableElementType(debugName, RsLanguage) {
    abstract override fun isParsable(seq: CharSequence, fileLanguage: Language, project: Project): Boolean

    /**
     * Must be non-null to make re-parsing work.
     * See [com.intellij.psi.impl.BlockSupportImpl.tryReparseNode]
     */
    final override fun createNode(text: CharSequence?): ASTNode? = LazyParseableElement(this, text)
}
