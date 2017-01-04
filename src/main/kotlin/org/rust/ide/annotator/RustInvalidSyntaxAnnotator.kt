package org.rust.ide.annotator

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.RustConstantRole
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.default
import org.rust.lang.core.psi.impl.mixin.role

class RustInvalidSyntaxAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustElementVisitor() {
        override fun visitPath(o: RustPathElement) {
            if (o.asRustPath == null) {
                holder.createErrorAnnotation(o, "Invalid path: self and super are allowed only at the beginning")
            }
        }

        override fun visitVis(o: RustVisElement) {
            if (o.parent is RustImplItemElement || o.parent is RustForeignModItemElement || isInTraitImpl(o)) {
                holder.createErrorAnnotation(o, "Visibility modifier is not allowed here")
            }
        }

        override fun visitConstant(o: RustConstantElement) {
            val title = if (o.static != null) "Static constant `${o.identifier.text}`" else "Constant `${o.identifier.text}`"
            when (o.role) {
                RustConstantRole.FREE -> {
                    deny(o.default, "$title cannot have the `default` qualifier")
                    require(o.expr, "$title must have a value", o)
                }
                RustConstantRole.TRAIT_CONSTANT -> {
                    deny(o.vis, "$title cannot have the `pub` qualifier")
                    deny(o.default, "$title cannot have the `default` qualifier")
                    deny(o.static, "Static constants are not allowed in traits")
                }
                RustConstantRole.IMPL_CONSTANT -> {
                    deny(o.static, "Static constants are not allowed in impl blocks")
                    require(o.expr, "$title must have a value", o)
                }
                RustConstantRole.FOREIGN -> {
                    deny(o.default, "$title cannot have the `default` qualifier")
                    require(o.static, "Only static constants are allowed in extern blocks", o.const)
                        ?: require(o.mut, "Non mutable static constants are not allowed in extern blocks", o.static, o.identifier)
                    deny(o.expr, "Static constants in extern blocks cannot have values", o.eq, o.expr)
                }
            }
        }

        private fun require(el: PsiElement?, message: String, vararg highlightElements: PsiElement?): Annotation? =
            if (el != null) null else holder.createErrorAnnotation(highlightElements.combinedRange() ?: TextRange.EMPTY_RANGE, message)

        private fun deny(el: PsiElement?, message: String, vararg highlightElements: PsiElement?): Annotation? =
            if (el == null) null else holder.createErrorAnnotation(highlightElements.combinedRange() ?: el.textRange, message)

        private fun Array<out PsiElement?>.combinedRange(): TextRange? {
            var range: TextRange? = null
            filterNotNull()
                .map { it.textRange }
                .forEach { range = range?.union(it) ?: it }
            return range
        }
    })

    private fun isInTraitImpl(o: RustVisElement): Boolean {
        val impl = o.parent?.parent
        return impl is RustImplItemElement && impl.traitRef != null
    }
}
