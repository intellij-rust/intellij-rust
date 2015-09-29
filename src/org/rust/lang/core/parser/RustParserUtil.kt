package org.rust.lang.core.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.TokenType
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.lexer.containsEOL
import kotlin.platform.platformStatic

public object RustParserUtil : GeneratedParserUtilBase() {

    @platformStatic
    public fun skipUntilEOL(b: PsiBuilder, level: Int) : Boolean {
        while (!b.eof()) {
            if (b.getTokenType() == TokenType.WHITE_SPACE
            ||  b.getTokenText()?.containsEOL() != null) return true;

            b.advanceLexer();
        }

        return false;
    }

    @JvmStatic
    public fun unpairedToken(b: PsiBuilder, level: Int): Boolean {
        when (b.tokenType) {
            RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE -> return false
            RustTokenElementTypes.LPAREN, RustTokenElementTypes.RPAREN -> return false
            RustTokenElementTypes.LBRACK, RustTokenElementTypes.RBRACK -> return false
            else -> {
                b.advanceLexer();
                return true
            }
        }
    }
}

