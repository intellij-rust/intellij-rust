/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*

class RsLiteralAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is RsLitExpr) return
        val literal = element.kind

        // Check suffix
        when (literal) {
            is RsLiteralKind.Integer, is RsLiteralKind.Float, is RsLiteralKind.String, is RsLiteralKind.Char -> {
                literal as RsLiteralWithSuffix
                val suffix = literal.suffix
                val validSuffixes = literal.validSuffixes
                if (!suffix.isNullOrEmpty() && suffix !in validSuffixes) {
                    holder.createErrorAnnotation(element, if (validSuffixes.isNotEmpty()) {
                        val validSuffixesStr = validSuffixes.map { "'$it'" }.joinToString()
                        "invalid suffix '$suffix' for ${literal.node.displayName}; " +
                            "the suffix must be one of: $validSuffixesStr"
                    } else {
                        "${literal.node.displayName} with a suffix is invalid"
                    })
                }
            }

            is RsLiteralKind.Boolean -> {
            }
        }

        // Check char literal length
        if (literal is RsLiteralKind.Char) {
            val value = literal.value
            when {
                value == null || value.isEmpty() -> "empty ${literal.node.displayName}"
                value.codePointCount(0, value.length) > 1 -> "too many characters in ${literal.node.displayName}"
                else -> null
            }?.let { holder.createErrorAnnotation(element, it) }
        }

        // Check delimiters
        if (literal is RsTextLiteral && literal.hasUnpairedQuotes) {
            holder.createErrorAnnotation(element, "unclosed ${literal.node.displayName}")
        }
    }
}

private val ASTNode.displayName: String
    get() = when (elementType) {
        INTEGER_LITERAL -> "integer literal"
        FLOAT_LITERAL -> "float literal"

        CHAR_LITERAL -> "char literal"
        BYTE_LITERAL -> "byte literal"

        STRING_LITERAL -> "string literal"
        BYTE_STRING_LITERAL -> "byte string literal"

        RAW_STRING_LITERAL -> "raw string literal"
        RAW_BYTE_STRING_LITERAL -> "raw byte string literal"

        else -> toString()
    }
