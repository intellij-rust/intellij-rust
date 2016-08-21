package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

class RustInvalidSyntaxAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustElementVisitor() {
        override fun visitPath(o: RustPathElement) {
            if (o.relativeModulePrefix is RelativeModulePrefix.Invalid) {
                holder.createErrorAnnotation(o, "Invalid path: self and super are allowed only at the beginning")
            }
        }

        override fun visitVis(o: RustVisElement) {
            if (o.parent is RustImplItemElement || o.parent is RustForeignModItemElement || isInTraitImpl(o)) {
                holder.createErrorAnnotation(o, "Visibility modifier is not allowed here")
            }
        }
    })

    private fun isInTraitImpl(o: RustVisElement): Boolean {
        val impl = o.parent?.parent
        return impl is RustImplItemElement && impl.traitRef != null
    }
}
