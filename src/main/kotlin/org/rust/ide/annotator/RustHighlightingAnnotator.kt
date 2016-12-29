package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RustColor
import org.rust.ide.highlight.RustHighlighter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.RustFunctionKind
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.core.psi.impl.mixin.kind
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.types.util.isPrimitive
import org.rust.lang.core.types.visitors.impl.RustTypificationEngine

// Highlighting logic here should be kept in sync with tags in RustColorSettingsPage
class RustHighlightingAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val highlightingInfo =
            // TODO(XXX): Much better would be to incorporate
            //            that behaviour into visitor
            if (element is RustReferenceElement) {
                val parent = element.parent
                val isPrimitiveType =
                    element is RustPathElement &&
                        parent is RustPathTypeElement &&
                        RustTypificationEngine.typifyType(parent).isPrimitive

                val color =
                    if (isPrimitiveType) {
                        RustColor.PRIMITIVE_TYPE
                    } else {
                        val ref = element.reference.resolve() ?: return
                        // Highlight the element dependent on what it's referencing.
                        HighlightingVisitor().compute(ref).color
                    }

                HighlightingInfo(element.referenceNameElement, color)
            } else {
                HighlightingVisitor().compute(element)
            }

        highlightingInfo.apply(holder)
    }

    private data class HighlightingInfo(val element: PsiElement?, val color: RustColor?) {
        fun apply(holder: AnnotationHolder) {
            if (element != null && color != null) {
                holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
            }
        }
    }

    private class HighlightingVisitor : RustComputingVisitor<HighlightingInfo>(default = HighlightingInfo(null, null)) {

        fun highlight(element: PsiElement?, color: RustColor?) = set { HighlightingInfo(element, color) }

        override fun visitLitExpr(o: RustLitExprElement) {
            // Re-highlight literals in attributes
            if (o.parent is RustMetaItemElement) {
                val literal = o.firstChild
                highlight(literal, RustHighlighter.map(literal.elementType))
            }
        }

        override fun visitElement(element: PsiElement) {
            if (element.elementType in RustTokenElementTypes.CONTEXTUAL_KEYWORDS) {
                highlight(element, RustColor.KEYWORD)
            }
        }

        override fun visitTypeParam(o: RustTypeParamElement) = highlight(o.identifier, RustColor.TYPE_PARAMETER)

        override fun visitAttr(o: RustAttrElement) = highlight(o, RustColor.ATTRIBUTE)

        override fun visitTraitRef(o: RustTraitRefElement) = highlight(o.path.identifier, RustColor.TRAIT)

        override fun visitPatBinding(o: RustPatBindingElement) {
            when {
                o.parentOfType<RustParameterElement>() != null -> highlight(o.identifier, RustColor.PARAMETER)
                o.isMut -> highlight(o.identifier, RustColor.MUT_BINDING)
            }
        }

        override fun visitEnumItem(o: RustEnumItemElement)       = highlight(o.identifier, RustColor.ENUM)
        override fun visitEnumVariant(o: RustEnumVariantElement) = highlight(o.identifier, RustColor.ENUM_VARIANT)

        override fun visitStructItem(o: RustStructItemElement)   = highlight(o.identifier, RustColor.STRUCT)
        override fun visitTraitItem(o: RustTraitItemElement)     = highlight(o.identifier, RustColor.TRAIT)
        override fun visitTypeItem(o: RustTypeItemElement)       = highlight(o.identifier, RustColor.TYPE_ALIAS)
        override fun visitModDeclItem(o: RustModDeclItemElement) = highlight(o.identifier, RustColor.MODULE)
        override fun visitModItem(o: RustModItemElement)         = highlight(o.identifier, RustColor.MODULE)

        override fun visitFieldDecl(o: RustFieldDeclElement)     = highlight(o.identifier, RustColor.FIELD)

        override fun visitExternCrateItem(o: RustExternCrateItemElement) = highlight(o.identifier, RustColor.CRATE)

        override fun visitMacroInvocation(m: RustMacroInvocationElement) = highlight(m, RustColor.MACRO)
        override fun visitMethodCallExpr(o: RustMethodCallExprElement)   = highlight(o.identifier, RustColor.METHOD)
        override fun visitTryExpr(o: RustTryExprElement)                 = highlight(o.q, RustColor.Q_OPERATOR)

        override fun visitFunction(o: RustFunctionElement) {
            val color = when (o.kind) {
                RustFunctionKind.FOREIGN, RustFunctionKind.FREE -> RustColor.FUNCTION
                RustFunctionKind.TRAIT_METHOD, RustFunctionKind.IMPL_METHOD ->
                    if (o.isStatic) RustColor.ASSOC_FUNCTION else RustColor.METHOD
            }
            highlight(o.identifier, color)
        }

        override fun visitSelfArgument(o: RustSelfArgumentElement) = highlight(o.self, RustColor.SELF_PARAMETER)
    }
}
