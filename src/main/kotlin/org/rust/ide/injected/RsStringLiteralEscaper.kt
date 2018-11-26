/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.utils.parseRustStringCharacters

/** See [com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper] */
private class RsNormalStringLiteralEscaper(host: RsLitExpr) : LiteralTextEscaper<RsLitExpr>(host) {
    private var outSourceOffsets: IntArray? = null

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val subText = rangeInsideHost.substring(myHost.text)
        val (offsets, result) = parseRustStringCharacters(subText, outChars)
        outSourceOffsets = offsets
        return result
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        val outSourceOffsets = outSourceOffsets!!
        val result = if (offsetInDecoded < outSourceOffsets.size) outSourceOffsets[offsetInDecoded] else -1
        return if (result == -1) {
            -1
        } else {
            (if (result <= rangeInsideHost.length) result else rangeInsideHost.length) + rangeInsideHost.startOffset
        }
    }

    override fun isOneLine(): Boolean = false
}

fun escaperForLiteral(lit: RsLitExpr): LiteralTextEscaper<RsLitExpr> {
    val elementType = lit.node.findChildByType(RS_ALL_STRING_LITERALS)?.elementType
    val isRaw = elementType == RAW_STRING_LITERAL || elementType == RAW_BYTE_STRING_LITERAL
    assert(isRaw || elementType == STRING_LITERAL || elementType == BYTE_STRING_LITERAL) {
        "`${lit.text}` is not a string literal"
    }
    return if (isRaw) RsSimpleMultiLineEscaper(lit) else RsNormalStringLiteralEscaper(lit)
}
