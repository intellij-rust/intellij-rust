/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.psi.LiteralTextEscaper
import org.rust.lang.core.psi.*
import org.rust.lang.utils.parseRustStringCharacters

private class RsNormalStringLiteralEscaper(host: RsLitExpr) : LiteralTextEscaperBase<RsLitExpr>(host) {
    override fun parseStringCharacters(chars: String, outChars: java.lang.StringBuilder): Pair<IntArray, Boolean> =
        parseRustStringCharacters(chars, outChars)

    override fun isOneLine(): Boolean = false
}

fun escaperForLiteral(lit: RsLitExpr): LiteralTextEscaper<RsLitExpr> {
    val elementType = lit.node.findChildByType(RS_ALL_STRING_LITERALS)?.elementType
    assert(elementType in RS_ALL_STRING_LITERALS) { "`${lit.text}` is not a string literal" }
    return if (elementType in RS_RAW_LITERALS) SimpleMultiLineTextEscaper(lit) else RsNormalStringLiteralEscaper(lit)
}
