/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType

enum class MacroBraces(val open: String, val close: String) {
    PARENS("(", ")"),
    BRACKS("[", "]"),
    BRACES("{", "}");

    fun wrap(text: CharSequence): String =
        open + text + close

    val needsSemicolon: Boolean
        get() = this != BRACES

    companion object {
        fun fromToken(token: IElementType): MacroBraces? = when (token) {
            RsElementTypes.LPAREN, RsElementTypes.RPAREN -> PARENS
            RsElementTypes.LBRACK, RsElementTypes.RBRACK -> BRACKS
            RsElementTypes.LBRACE, RsElementTypes.RBRACE -> BRACES
            else -> null
        }
    }
}
