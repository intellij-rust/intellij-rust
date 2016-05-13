package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.colorscheme.RustColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.core.psi.impl.mixin.isStatic

// Highlighting logic here should be kept in sync with tags in RustColorSettingsPage
class RustHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustVisitor() {
        override fun visitAttr(o: RustAttr) {
            holder.highlight(o, RustColor.ATTRIBUTE)
        }

        override fun visitMacroInvocation(m: RustMacroInvocation) {
            holder.highlight(m, RustColor.MACRO)
        }

        override fun visitTypeParam(o: RustTypeParam) {
            holder.highlight(o, RustColor.TYPE_PARAMETER)
        }

        override fun visitPatBinding(o: RustPatBinding) {
            if (o.isMut) {
                holder.highlight(o.identifier, RustColor.MUT_BINDING)
            }
        }

        override fun visitPath(o: RustPath) {
            o.reference.resolve().let {
                if (it is RustPatBinding && it.isMut) {
                    holder.highlight(o.identifier, RustColor.MUT_BINDING)
                }
            }
        }

        override fun visitFnItem(o: RustFnItem) {
            holder.highlight(o.identifier, RustColor.FUNCTION_DECLARATION)
        }

        override fun visitImplMethodMember(o: RustImplMethodMember) {
            val color = if (o.isStatic) RustColor.STATIC_METHOD else RustColor.INSTANCE_METHOD
            holder.highlight(o.identifier, color)
        }

        override fun visitTraitMethodMember(o: RustTraitMethodMember) {
            val color = if (o.isStatic) RustColor.STATIC_METHOD else RustColor.INSTANCE_METHOD
            holder.highlight(o.identifier, color)
        }
    })
}

private fun AnnotationHolder.highlight(element: PsiElement?, color: RustColor) {
    if (element != null) {
        createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
