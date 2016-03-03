package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.rust.ide.colorscheme.RustColors
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.isMut

class RustHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustVisitor() {
        override fun visitAttr(o: RustAttr) {
            holder.highlight(o, RustColors.ATTRIBUTE)
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
    })
}

private fun AnnotationHolder.highlight(element: PsiElement?, textAttributes: TextAttributesKey) {
    if (element != null) {
        createInfoAnnotation(element, null).textAttributes = textAttributes
    }
}
