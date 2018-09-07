/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.parse

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object TomlParserUtil : GeneratedParserUtilBase() {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun remap(builder: PsiBuilder, level: Int, from: IElementType, to: IElementType): Boolean {
        if (builder.tokenType == from) {
            builder.remapCurrentToken(to)
            builder.advanceLexer()
            return true
        }
        return false
    }

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun any(builder: PsiBuilder, level: Int): Boolean = true

    @JvmStatic
    fun atSameLine(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
        val marker = enter_section_(builder)
        builder.eof() // skip whitespace
        val isSameLine = !isNextAfterNewLine(builder)
        if (!isSameLine) addVariant(builder, "VALUE")
        val result = isSameLine && parser.parse(builder, level)
        exit_section_(builder, marker, null, result)
        return result
    }

    @JvmStatic
    fun atNewLine(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
        val marker = enter_section_(builder)
        builder.eof() // skip whitespace
        val result = isNextAfterNewLine(builder) && parser.parse(builder, level)
        exit_section_(builder, marker, null, result)
        return result
    }
}

private fun isNextAfterNewLine(b: PsiBuilder): Boolean {
    val prevToken = b.rawLookup(-1)
    return prevToken == null || prevToken == TokenType.WHITE_SPACE && b.rawLookupText(-1).contains("\n")
}

private fun PsiBuilder.rawLookupText(steps: Int): CharSequence {
    val start = rawTokenTypeStart(steps)
    val end = rawTokenTypeStart(steps + 1)
    return if (start == -1 || end == -1) "" else originalText.subSequence(start, end)
}
