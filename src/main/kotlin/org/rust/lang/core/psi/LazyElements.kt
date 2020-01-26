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
import com.intellij.psi.tree.IReparseableElementType
import org.rust.lang.RsLanguage
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.ext.macroName

fun factory(name: String): IElementType = when (name) {
    "MACRO_ARGUMENT" -> RsMacroArgumentElementType
    "MACRO_BODY" -> RsMacroBodyElementType
    else -> error("Unknown element $name")
}

private object RsMacroArgumentElementType : RsReparseableElementTypeBase("MACRO_ARGUMENT") {
    override fun isParsable(parent: ASTNode?, buffer: CharSequence, fileLanguage: Language, project: Project): Boolean {
        val parentMacro = parent?.psi as? RsMacroCall ?: return false

        // Special macros are not reparseable because a change in the content of a macro argument
        // can change a type of the argument (e.g. to VEC_MACRO_ARGUMENT)
        if (RustParserUtil.isSpecialMacro(parentMacro.macroName)) return false

        return RustParserUtil.hasProperTokenTreeBraceBalance(buffer, RsLexer())
    }
}

private object RsMacroBodyElementType : RsTTBodyLazyElementTypeBase("MACRO_BODY")

private abstract class RsTTBodyLazyElementTypeBase(debugName: String) : RsReparseableElementTypeBase(debugName) {
    override fun isParsable(buffer: CharSequence, fileLanguage: Language, project: Project): Boolean =
        RustParserUtil.hasProperTokenTreeBraceBalance(buffer, RsLexer())
}

private abstract class RsReparseableElementTypeBase(debugName: String) : IReparseableElementType(debugName, RsLanguage) {
    /**
     * Must be non-null to make re-parsing work.
     * See [com.intellij.psi.impl.BlockSupportImpl.tryReparseNode]
     */
    final override fun createNode(text: CharSequence?): ASTNode? = LazyParseableElement(this, text)
}
