package org.rust.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustAttr


public class RustAnnotator : Annotator {
    object Colors {
        val ATTRIBUTE = TextAttributesKey.createTextAttributesKey("org.rust.ATTRIBUTE",
                DefaultLanguageHighlighterColors.METADATA)
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RustAttr -> {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.textAttributes = Colors.ATTRIBUTE
            }
        }
    }
}