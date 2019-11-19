/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsElementTypes.*

enum class MacroBraces(
    val openText: String,
    val closeText: String,
    val openToken: IElementType,
    val closeToken: IElementType
) {
    PARENS("(", ")", LPAREN, RPAREN),
    BRACKS("[", "]", LBRACK, RBRACK),
    BRACES("{", "}", LBRACE, RBRACE);

    fun wrap(text: CharSequence): String =
        openText + text + closeText

    val needsSemicolon: Boolean
        get() = this != BRACES

    companion object {
        fun fromToken(token: IElementType): MacroBraces? = when (token) {
            LPAREN, RPAREN -> PARENS
            LBRACK, RBRACK -> BRACKS
            LBRACE, RBRACE -> BRACES
            else -> null
        }

        fun fromTokenOrFail(token: IElementType): MacroBraces =
            fromToken(token) ?: error("Given token is not a brace: $token")
    }
}
