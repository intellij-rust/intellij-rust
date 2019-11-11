/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.lang.psi.LiteralTextEscaperBase
import com.intellij.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.psi.LiteralTextEscaper
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.utils.parseRustStringCharacters

private class RsNormalStringLiteralEscaper(host: RsLitExpr) : LiteralTextEscaperBase<RsLitExpr>(host) {
    override fun parseStringCharacters(chars: String, outChars: java.lang.StringBuilder): Pair<IntArray, Boolean> =
        parseRustStringCharacters(chars, outChars)

    override fun isOneLine(): Boolean = false
}

fun escaperForLiteral(lit: RsLitExpr): LiteralTextEscaper<RsLitExpr> {
    val elementType = lit.node.findChildByType(RS_ALL_STRING_LITERALS)?.elementType
    val isRaw = elementType == RAW_STRING_LITERAL || elementType == RAW_BYTE_STRING_LITERAL
    assert(isRaw || elementType == STRING_LITERAL || elementType == BYTE_STRING_LITERAL) {
        "`${lit.text}` is not a string literal"
    }
    return if (isRaw) SimpleMultiLineTextEscaper(lit) else RsNormalStringLiteralEscaper(lit)
}
