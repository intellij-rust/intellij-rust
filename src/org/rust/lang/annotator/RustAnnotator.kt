package org.rust.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.rust.lang.colorscheme.RustColors
import org.rust.lang.core.psi.RustAttr
import org.rust.lang.core.psi.RustMacroExpr


public class RustAnnotator : Annotator {
    private fun addTextAttributes(element: PsiElement?, holder: AnnotationHolder, textAttributes: TextAttributesKey) {
        if (element != null) {
            holder.createInfoAnnotation(element, null).textAttributes = textAttributes
        }
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RustAttr -> {
                addTextAttributes(element, holder, RustColors.ATTRIBUTE)
            }
            is RustMacroExpr -> {
                addTextAttributes(element.identifier, holder, RustColors.MACRO)
                addTextAttributes(element.excl, holder, RustColors.MACRO)
            }
        }
    }
}
