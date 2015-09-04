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

}

