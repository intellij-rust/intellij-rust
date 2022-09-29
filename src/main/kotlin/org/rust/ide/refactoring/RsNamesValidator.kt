/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import org.rust.lang.core.lexer.getRustLexerTokenType
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER

class RsNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean = isKeyword(name)

    override fun isIdentifier(name: String, project: Project?): Boolean = isIdentifier(name)

    companion object {
        val RESERVED_LIFETIME_NAMES: Set<String> = setOf("'static", "'_")

        fun isIdentifier(name: String): Boolean = when (name.getRustLexerTokenType()) {
            IDENTIFIER, QUOTE_IDENTIFIER -> true
            else -> false
        }

        fun isKeyword(name: String): Boolean = name.getRustLexerTokenType() in RS_KEYWORDS
    }
}

fun isValidRustVariableIdentifier(name: String): Boolean = name.getRustLexerTokenType() == IDENTIFIER
