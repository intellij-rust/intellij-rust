/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER

class RsNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean {
        return getLexerType(name) in RS_KEYWORDS
    }

    override fun isIdentifier(name: String, project: Project?): Boolean {
        return when (getLexerType(name)) {
            IDENTIFIER, QUOTE_IDENTIFIER -> true
            else -> false
        }
    }


    companion object {
        val PredefinedLifetimes = arrayOf("'static")
    }
}

fun isValidRustVariableIdentifier(name: String): Boolean = getLexerType(name) == IDENTIFIER

private fun getLexerType(text: String): IElementType? {
    val lexer = RsLexer()
    lexer.start(text)
    return if (lexer.tokenEnd == text.length) lexer.tokenType else null
}
