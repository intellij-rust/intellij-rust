package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.rust.ide.colorscheme.RustColors
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isMut

class RustAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) =
        element.accept(RustAnnotatorVisitor(holder))
}

private class RustAnnotatorVisitor(private val holder: AnnotationHolder) : RustVisitor() {
    override fun visitElement(element: PsiElement?) {
        when (element) {
            is RustLiteral -> visitLiteral(element)
            else           -> super.visitElement(element)
        }
    }

    override fun visitAttr(o: RustAttr) {
        holder.highlight(o, RustColors.ATTRIBUTE)
    }

    fun visitLiteral(o: RustLiteral) {
        // TODO: Use some human-friendly names instead of debug ones
        val displayName = o.toString()

        // Check char literal length
        when (o.tokenType) {
            RustTokenElementTypes.BYTE_LITERAL, RustTokenElementTypes.CHAR_LITERAL -> {
                val value = o.valueString
                when {
                    value.isNullOrEmpty() -> holder.createErrorAnnotation(o, "empty $displayName")
                    value.length > 1      -> holder.createErrorAnnotation(o, "too many characters in $displayName")
                }
            }
        }

        // Check delimiters
        if (!o.hasPairedDelimiters) {
            holder.createErrorAnnotation(o, "unclosed $displayName")
        }

        // Check suffix
        val suffix = o.suffix
        val possibleSuffixes = o.possibleSuffixes
        if (!suffix.isNullOrEmpty() && suffix !in possibleSuffixes) {
            holder.createErrorAnnotation(o, if (possibleSuffixes.isNotEmpty()) {
                "invalid suffix '$suffix' for $displayName; the suffix must be one of: ${possibleSuffixes.joinToString()}"
            } else {
                "$displayName with a suffix is invalid"
            })
        }

        // TODO: Check numeric literals boundaries
    }

    override fun visitMacroExpr(o: RustMacroExpr) {
        holder.highlight(o.identifier, RustColors.MACRO)
        holder.highlight(o.excl, RustColors.MACRO)
    }

    override fun visitTypeParam(o: RustTypeParam) {
        holder.highlight(o, RustColors.TYPE_PARAMETER)
    }

    override fun visitPatBinding(o: RustPatBinding) {
        if (o.isMut) {
            holder.highlight(o.identifier, RustColors.MUT_BINDING)
        }
    }

    override fun visitPathPart(o: RustPathPart) {
        o.reference.resolve().let {
            if (it is RustPatBinding && it.isMut) {
                holder.highlight(o.identifier, RustColors.MUT_BINDING)
            }
        }
    }
}

private fun AnnotationHolder.highlight(element: PsiElement?, textAttributes: TextAttributesKey) {
    if (element != null) {
        createInfoAnnotation(element, null).textAttributes = textAttributes
    }
}
