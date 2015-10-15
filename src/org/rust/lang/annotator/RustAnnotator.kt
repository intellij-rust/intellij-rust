package org.rust.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.colorscheme.RustColors
import org.rust.lang.core.psi.RustAttr


public class RustAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RustAttr -> {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.textAttributes = RustColors.ATTRIBUTE
            }
        }
    }
}