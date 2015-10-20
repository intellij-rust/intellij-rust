package org.rust.lang.core.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.util.Key
import com.intellij.psi.TokenType
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.lexer.containsEOL

public object RustParserUtil : GeneratedParserUtilBase() {

    private val STRUCT_ALLOWED: Key<Boolean> = Key("org.rust.STRUCT_ALLOWED")

    private fun PsiBuilder.isStructAllowed(): Boolean {
        return getUserData(STRUCT_ALLOWED) ?: true
    }

    private fun PsiBuilder.setStructAllowed(value: Boolean) {
        return putUserData(STRUCT_ALLOWED, value)
    }

    @JvmStatic
    public fun checkStructAllowed(b: PsiBuilder, level: Int) : Boolean = b.isStructAllowed()

    @JvmStatic
    public fun checkBraceAllowed(b: PsiBuilder, level: Int) : Boolean {
        return b.isStructAllowed() || b.tokenType != RustTokenElementTypes.LBRACE
    }

    @JvmStatic
    public fun withoutStructLiterals(b: PsiBuilder, level: Int, parser: GeneratedParserUtilBase.Parser): Boolean {
        val old = b.isStructAllowed()
        b.setStructAllowed(false)
        val result = parser.parse(b, level)
        b.setStructAllowed(old)
        return result
    }

    @JvmStatic
    public fun withStructLiterals(b: PsiBuilder, level: Int, parser: GeneratedParserUtilBase.Parser): Boolean {
        val old = b.isStructAllowed()
        b.setStructAllowed(true)
        val result = parser.parse(b, level)
        b.setStructAllowed(old)
        return result
    }


    @JvmStatic
    public fun skipUntilEOL(b: PsiBuilder, level: Int): Boolean {
        while (!b.eof()) {
            if (b.tokenType == TokenType.WHITE_SPACE
                    || b.tokenText?.containsEOL() != null) return true;

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

