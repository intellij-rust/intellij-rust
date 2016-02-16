package org.rust.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.*

abstract class RustLiteralLexer : LexerBaseEx() {
    companion object {
        /**
         * Create an instance of [RustLiteralLexer] able to handle [INTEGER_LITERAL] and [FLOAT_LITERAL].
         */
        fun forNumericLiterals(): RustLiteralLexer = TODO()

        /**
         * Create an instance of [RustLiteralLexer] able to handle [CHAR_LITERAL], [BYTE_LITERAL], [STRING_LITERAL], [BYTE_STRING_LITERAL].
         */
        fun forStringLiterals(): RustLiteralLexer = TODO()

        /**
         * Create an instance of [RustLiteralLexer] able to handle [RAW_STRING_LITERAL] and [RAW_BYTE_STRING_LITERAL].
         */
        fun forRawStringLiterals(): RustLiteralLexer = TODO()

        /**
         * Create an instance of [RustLiteralLexer] suitable for given [IElementType].
         *
         * For the set of supported token types see [LITERALS_TOKEN_SET].
         *
         * @throws IllegalArgumentException when given token type is unsupported
         */
        fun of(tokenType: IElementType): RustLiteralLexer = when (tokenType) {
            INTEGER_LITERAL,
            FLOAT_LITERAL           -> forNumericLiterals()

            CHAR_LITERAL,
            BYTE_LITERAL,
            STRING_LITERAL,
            BYTE_STRING_LITERAL     -> forStringLiterals()

            RAW_STRING_LITERAL,
            RAW_BYTE_STRING_LITERAL -> forRawStringLiterals()

            else                    -> throw IllegalArgumentException("unsupported literal type")
        }
    }
}
