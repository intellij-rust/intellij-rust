/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.lexer.RustEscapesLexer
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.utils.unescapeRust

interface RsComplexLiteral {
    val node: ASTNode
    val offsets: LiteralOffsets
}

interface RsLiteralWithSuffix : RsComplexLiteral {
    val suffix: String? get() = offsets.suffix?.substring(node.text)
    val validSuffixes: List<String>
}

interface RsTextLiteral {
    val value: String?
    val hasUnpairedQuotes: Boolean
}

sealed class RsLiteralKind(val node: ASTNode) {
    class Boolean(node: ASTNode) : RsLiteralKind(node) {
        val value: kotlin.Boolean = node.chars == "true"
    }

    class Integer(node: ASTNode) : RsLiteralKind(node), RsLiteralWithSuffix {
        override val validSuffixes: List<kotlin.String>
            get() = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "u128", "i128", "isize", "usize")

        override val offsets: LiteralOffsets by lazy { offsetsForNumber(node) }
    }

    class Float(node: ASTNode) : RsLiteralKind(node), RsLiteralWithSuffix {
        override val validSuffixes: List<kotlin.String>
            get() = listOf("f32", "f64")

        val value: Double? get() = offsets.value?.substring(node.text)
            ?.filter { it != '_' }
            ?.let {
                try {
                    it.toDouble()
                } catch(e: NumberFormatException) {
                    null
                }
            }


        override val offsets: LiteralOffsets by lazy { offsetsForNumber(node) }
    }

    class String(node: ASTNode, val isByte: kotlin.Boolean) : RsLiteralKind(node), RsLiteralWithSuffix, RsTextLiteral {
        override val offsets: LiteralOffsets by lazy { offsetsForText(node) }

        override val validSuffixes: List<kotlin.String> get() = emptyList()

        override val hasUnpairedQuotes: kotlin.Boolean
            get() = offsets.openDelim == null || offsets.closeDelim == null

        override val value: kotlin.String? get() {
            val rawValue = offsets.value?.substring(node.text)

            return if (node.elementType in RS_RAW_LITERALS)
                rawValue
            else
                rawValue?.unescapeRust(RustEscapesLexer.of(node.elementType))
        }
    }

    class Char(node: ASTNode, val isByte: kotlin.Boolean) : RsLiteralKind(node), RsLiteralWithSuffix, RsTextLiteral {
        override val offsets: LiteralOffsets by lazy { offsetsForText(node) }

        override val validSuffixes: List<kotlin.String> get() = emptyList()

        override val hasUnpairedQuotes: kotlin.Boolean
            get() = offsets.openDelim == null || offsets.closeDelim == null

        override val value: kotlin.String?
            get() = offsets.value?.substring(node.text)
                ?.unescapeRust(RustEscapesLexer.of(node.elementType))
    }

    companion object {
        fun fromAstNode(node: ASTNode): RsLiteralKind? = when (node.elementType) {
            BOOL_LITERAL -> Boolean(node)
            INTEGER_LITERAL -> Integer(node)
            FLOAT_LITERAL -> Float(node)

            STRING_LITERAL, RAW_STRING_LITERAL -> String(node, isByte = false)
            BYTE_STRING_LITERAL, RAW_BYTE_STRING_LITERAL -> String(node, isByte = true)

            CHAR_LITERAL -> Char(node, isByte = false)
            BYTE_LITERAL -> Char(node, isByte = true)
            else -> null
        }
    }

}

val RsLitExpr.kind: RsLiteralKind? get() {
    val literalAstNode = this.node.findChildByType(RS_LITERALS) ?: return null
    return RsLiteralKind.fromAstNode(literalAstNode)
        ?: error("Unknown literal: $literalAstNode (`$text`)")
}

fun offsetsForNumber(node: ASTNode): LiteralOffsets {
    val (start, digits) = when (node.text.take(2)) {
        "0b" -> 2 to "01"
        "0o" -> 2 to "012345678"
        "0x" -> 2 to "0123456789abcdefABCDEF"
        else -> 0 to "0123456789"
    }

    var hasExponent = false
    node.text.substring(start).forEachIndexed { i, ch ->
        if (!hasExponent && ch in "eE") {
            hasExponent = true
        } else if (ch !in digits && ch !in "+-_.") {
            return LiteralOffsets(
                value = TextRange.create(0, i + start),
                suffix = TextRange(i + start, node.textLength))
        }
    }

    return LiteralOffsets(value = TextRange.allOf(node.text))

}

fun offsetsForText(node: ASTNode): LiteralOffsets {
    when (node.elementType) {
        RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL ->
            return offsetsForRawText(node)
    }

    val text = node.text
    val quote = when (node.elementType) {
        BYTE_LITERAL, CHAR_LITERAL -> '\''
        else -> '"'
    }

    val prefixEnd = locatePrefix(node)

    val openDelimEnd = doLocate(node, prefixEnd) {
        assert(text[it] == quote) { "expected open delimiter `$quote` but found `${text[it]}`" }
        it + 1
    }

    val valueEnd = doLocate(node, openDelimEnd, fun(start: Int): Int {
        var escape = false
        text.substring(start).forEachIndexed { i, ch ->
            if (escape) {
                escape = false
            } else when (ch) {
                '\\' -> escape = true
                quote -> return i + start
            }
        }
        return node.textLength
    })

    val closeDelimEnd = doLocate(node, valueEnd) {
        assert(text[it] == quote) { "expected close delimiter `$quote` but found `${text[it]}`" }
        it + 1
    }

    return LiteralOffsets.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, node.textLength)
}

private fun offsetsForRawText(node: ASTNode): LiteralOffsets {
    val text = node.text
    val textLength = node.textLength

    val prefixEnd = locatePrefix(node)

    val hashes = run {
        var pos = prefixEnd
        while (pos < textLength && text[pos] == '#') {
            pos++
        }
        pos - prefixEnd
    }

    val openDelimEnd = doLocate(node, prefixEnd) {
        assert(textLength - it >= 1 + hashes && text[it] == '#' || text[it] == '"') { "expected open delim" }
        it + 1 + hashes
    }

    val valueEnd = doLocate(node, openDelimEnd, fun(start: Int): Int {
        text.substring(start).forEachIndexed { i, ch ->
            if (start + i + hashes < textLength &&
                ch == '"' &&
                text.subSequence(start + i + 1, start + i + 1 + hashes).all { it == '#' }) {
                return i + start
            }
        }
        return textLength
    })

    val closeDelimEnd = doLocate(node, valueEnd) {
        assert(textLength - it >= 1 + hashes && text[it] == '"') { "expected close delim" }
        it + 1 + hashes
    }

    return LiteralOffsets.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, textLength)
}

private fun locatePrefix(node: ASTNode): Int {
    node.text.forEachIndexed { i, ch ->
        if (!ch.isLetter()) {
            return i
        }
    }
    return node.textLength
}


private inline fun doLocate(node: ASTNode, start: Int, locator: (Int) -> Int): Int =
    if (start >= node.textLength) start else locator(start)
