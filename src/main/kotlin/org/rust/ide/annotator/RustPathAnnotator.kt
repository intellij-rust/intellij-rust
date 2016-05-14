package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RelativeModulePrefix
import org.rust.lang.core.psi.RustPath

class RustPathAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is RustPath) return
        if (element.relativeModulePrefix is RelativeModulePrefix.Invalid) {
            holder.createErrorAnnotation(element, "Invalid path: self and super are allowed only at the beginning")
        }
    }
}
