package org.rust.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.rust.lang.colorscheme.RustColors
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.isMut


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
            is RustTypeParam -> {
                addTextAttributes(element, holder, RustColors.TYPE_PARAMETER)
            }
            is RustPatBinding -> {
                if (element.isMut) {
                    addTextAttributes(element.identifier, holder, RustColors.MUT_BINDING)
                }
            }
            is RustPathPart -> {
                element.reference.resolve().let {
                    if (it is RustPatBinding && it.isMut) {
                        addTextAttributes(element.identifier, holder, RustColors.MUT_BINDING)
                    }
                }
            }
        }
    }
}
