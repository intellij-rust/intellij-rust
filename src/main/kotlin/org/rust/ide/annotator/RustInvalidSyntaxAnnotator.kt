package org.rust.ide.annotator

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.*

class RustInvalidSyntaxAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) = element.accept(object : RustElementVisitor() {
        override fun visitPath(o: RustPathElement) {
            if (o.asRustPath == null) {
                holder.createErrorAnnotation(o, "Invalid path: self and super are allowed only at the beginning")
            }
        }

        override fun visitVis(o: RustVisElement) {
            if (o.parent is RustImplItemElement || o.parent is RustForeignModItemElement || isInTraitImpl(o)) {
                holder.createErrorAnnotation(o, "Unnecessary visibility qualifier [E0449]")
            }
        }

        override fun visitTypeAlias(o: RustTypeAliasElement) {
            val title = "Type `${o.identifier.text}`"
            when (o.role) {
                RustTypeAliasRole.FREE -> {
                    deny(o.default, "$title cannot have the `default` qualifier")
                    deny(o.typeParamBounds, "$title cannot have type parameter bounds")
                    require(o.type, "Aliased type must be provided for type `${o.identifier.text}`", o)
                }
                RustTypeAliasRole.TRAIT_ASSOC_TYPE -> {
                    deny(o.default, "$title cannot have the `default` qualifier")
                    deny(o.vis, "$title cannot have the `pub` qualifier")
                    deny(o.genericParams, "$title cannot have generic parameters")
                    deny(o.whereClause, "$title cannot have `where` clause")
                }
                RustTypeAliasRole.IMPL_ASSOC_TYPE -> {
                    val impl = o.parent as? RustImplItemElement ?: return
                    if (impl.`for` == null) {
                        holder.createErrorAnnotation(o, "Associated types are not allowed in inherent impls [E0202]")
                    } else {
                        deny(o.genericParams, "$title cannot have generic parameters")
                        deny(o.whereClause, "$title cannot have `where` clause")
                        deny(o.typeParamBounds, "$title cannot have type parameter bounds")
                        require(o.type, "Aliased type must be provided for type `${o.identifier.text}`", o)
                    }
                }
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
            if (el != null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: TextRange.EMPTY_RANGE, message)

        private fun deny(el: PsiElement?, message: String, vararg highlightElements: PsiElement?): Annotation? =
            if (el == null) null else holder.createErrorAnnotation(highlightElements.combinedRange ?: el.textRange, message)

        private val Array<out PsiElement?>.combinedRange: TextRange?
            get() = if (isEmpty())
                null
            else filterNotNull()
                .map { it.textRange }
                .reduce(TextRange::union)
    })

    private fun isInTraitImpl(o: RustVisElement): Boolean {
        val impl = o.parent?.parent
        return impl is RustImplItemElement && impl.traitRef != null
    }
}
