package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.RustVisitorEx

class RustLiteralAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) =
        element.accept(RustLiteralAnnotatorVisitor(holder))
}

private class RustLiteralAnnotatorVisitor(private val holder: AnnotationHolder) : RustVisitorEx() {
    override fun visitLiteral(literal: RustLiteral) {
        // Check suffix
        val suffix = literal.suffix
        val possibleSuffixes = literal.possibleSuffixes
        if (!suffix.isNullOrEmpty() && suffix !in possibleSuffixes) {
            holder.createErrorAnnotation(literal as PsiElement, if (possibleSuffixes.isNotEmpty()) {
                val possibleSuffixesStr = possibleSuffixes.map { "'$it'" }.joinToString()
                "invalid suffix '$suffix' for ${literal.displayName}; the suffix must be one of: $possibleSuffixesStr"
            } else {
                "${literal.displayName} with a suffix is invalid"
            })
        }
    }

    override fun visitNumericLiteral(literal: RustLiteral.Number) {
        // TODO: Check numeric literals boundaries
        super.visitNumericLiteral(literal)
    }

    override fun visitTextLiteral(literal: RustLiteral.Text) {
        // Check char literal length
        when (literal.tokenType) {
            BYTE_LITERAL, CHAR_LITERAL -> when {
                literal.value.isNullOrEmpty() ->
                    holder.createErrorAnnotation(literal as PsiElement, "empty ${literal.displayName}")
                literal.value!!.length > 1    ->
                    holder.createErrorAnnotation(literal as PsiElement, "too many characters in ${literal.displayName}")
            }
        }

        // Check delimiters
        if (!literal.hasPairedQuotes) {
            holder.createErrorAnnotation(literal as PsiElement, "unclosed ${literal.displayName}")
        }

        super.visitTextLiteral(literal)
    }
}

// TODO: Make this more generic
private val PsiElement.displayName: String
    get() = when (node.elementType) {
        INTEGER_LITERAL         -> "numeric literal"
        FLOAT_LITERAL           -> "float literal"

        CHAR_LITERAL            -> "char literal"
        BYTE_LITERAL            -> "byte literal"

        STRING_LITERAL          -> "string literal"
        BYTE_STRING_LITERAL     -> "byte string literal"

        RAW_STRING_LITERAL      -> "raw string literal"
        RAW_BYTE_STRING_LITERAL -> "raw byte string literal"

        else                    -> toString()
    }
