package org.rust.lang.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER

class RsNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean = getLexerType(name) in RS_KEYWORDS

    override fun isIdentifier(name: String, project: Project?): Boolean =
        when (getLexerType(name)) {
            IDENTIFIER, QUOTE_IDENTIFIER -> true
            else -> false
        }

    private fun getLexerType(text: String): IElementType? {
        val lexer = RsLexer()
        lexer.start(text)
        return if (lexer.tokenEnd == text.length) lexer.tokenType else null
    }

    companion object {
        val PredefinedLifetimes = arrayOf("'static")
    }
}
