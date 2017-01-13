package org.rust.ide.annotator

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsLiteral
import org.rust.lang.core.psi.RsTokenElementTypes.*
import org.rust.lang.core.psi.visitors.RustVisitorEx

class RsLiteralAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustVisitorEx() {
        fun visit(literal: RsLiteral) {
            // Check suffix
            val suffix = literal.suffix
            val possibleSuffixes = literal.possibleSuffixes
            if (!suffix.isNullOrEmpty() && suffix !in possibleSuffixes) {
                holder.literalError(literal, if (possibleSuffixes.isNotEmpty()) {
                    val possibleSuffixesStr = possibleSuffixes.map { "'$it'" }.joinToString()
                    "invalid suffix '$suffix' for ${literal.displayName}; " +
                        "the suffix must be one of: $possibleSuffixesStr"
                } else {
                    "${literal.displayName} with a suffix is invalid"
                })
            }
        }

        override fun visitNumericLiteral(literal: RsLiteral.Number) {
            // TODO: Check numeric literals boundaries
            visit(literal)
        }

        override fun visitTextLiteral(literal: RsLiteral.Text) {
            // Check char literal length
            when (literal.elementType) {
                BYTE_LITERAL, CHAR_LITERAL -> {
                    val value = literal.value
                    when {
                        value == null || value.isEmpty() -> "empty ${literal.displayName}"
                        value.length > 1 -> "too many characters in ${literal.displayName}"
                        else -> null
                    }?.let { holder.literalError(literal, it) }
                }
            }

            // Check delimiters
            if (literal.hasUnpairedQuotes) {
                holder.literalError(literal, "unclosed ${literal.displayName}")
            }

            visit(literal)
        }
    })
}

private fun AnnotationHolder.literalError(literal: RsLiteral, errorMessage: String): Annotation? =
    createErrorAnnotation(literal as PsiElement, errorMessage)

private val PsiElement.displayName: String
    get() = node.elementType.displayName

// TODO: Make this more generic
private val IElementType.displayName: String
    get() = when (this) {
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
