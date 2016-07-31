package org.rust.lang.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.psi.RustKeywordTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.IDENTIFIER

class RustNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean =
        isKeyword(name, project, true)

    fun isKeyword(name: String, project: Project?, withPrimitives: Boolean): Boolean =
        getLexerType(name) is RustKeywordTokenType || (withPrimitives && name in PrimitiveTypes)

    override fun isIdentifier(name: String, project: Project?): Boolean =
        isIdentifier(name, project, true)

    fun isIdentifier(name: String, project: Project?, withPrimitives: Boolean): Boolean =
        if (withPrimitives) {
            getLexerType(name) == IDENTIFIER && name !in PrimitiveTypes
        } else {
            getLexerType(name) == IDENTIFIER
        }

    private fun getLexerType(text: String): IElementType? {
        val lexer = RustLexer()
        lexer.start(text)
        if (lexer.tokenEnd == text.length) {
            return lexer.tokenType
        } else {
            return null
        }
    }

    companion object {
        val PrimitiveTypes = arrayOf(
            "bool",
            "char",
            "i8",
            "i16",
            "i32",
            "i64",
            "u8",
            "u16",
            "u32",
            "u64",
            "isize",
            "usize",
            "f32",
            "f64",
            "str"
        )
    }
}
