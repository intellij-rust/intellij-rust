/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*

class RsLiteralAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element !is RsLitExpr) return
        val literal = element.kind

        // Check suffix
        when (literal) {
            is RsLiteralKind.Integer, is RsLiteralKind.Float, is RsLiteralKind.String, is RsLiteralKind.Char -> {
                literal as RsLiteralWithSuffix
                val suffix = literal.suffix
                val validSuffixes = literal.validSuffixes
                if (!suffix.isNullOrEmpty() && suffix !in validSuffixes) {
                    val message = if (validSuffixes.isNotEmpty()) {
                        val validSuffixesStr = validSuffixes.joinToString { "'$it'" }
                        RsBundle.message("inspection.message.invalid.suffix.for.suffix.must.be.one", suffix, literal.node.displayName, validSuffixesStr)
                    } else {
                        RsBundle.message("inspection.message.with.suffix.invalid", literal.node.displayName)
                    }

                    holder.newAnnotation(HighlightSeverity.ERROR, message).create()
                }
            }
            else -> Unit
        }

        // Check char literal length
        if (literal is RsLiteralKind.Char) {
            val value = literal.value
            @Nls val errorMessage = when {
                value.isNullOrEmpty() -> RsBundle.message("empty.0", literal.node.displayName)
                value.codePointCount(0, value.length) > 1 -> RsBundle.message("too.many.characters.in.0", literal.node.displayName)
                else -> null
            }
            if (errorMessage != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, errorMessage).create()
            }
        }

        // Check delimiters
        if (literal is RsTextLiteral && literal.hasUnpairedQuotes) {
            holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.unclosed", literal.node.displayName)).create()
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
        CSTRING_LITERAL -> "C string literal"

        RAW_STRING_LITERAL -> "raw string literal"
        RAW_BYTE_STRING_LITERAL -> "raw byte string literal"
        RAW_CSTRING_LITERAL -> "raw C string literal"

        else -> toString()
    }
