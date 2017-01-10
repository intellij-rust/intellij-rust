package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RustColor
import org.rust.ide.highlight.RustHighlighter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.RustFunctionRole
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.core.psi.impl.mixin.isStatic
import org.rust.lang.core.psi.impl.mixin.role
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.isPrimitive
import org.rust.lang.core.types.visitors.impl.RustTypificationEngine

// Highlighting logic here should be kept in sync with tags in RustColorSettingsPage
class RustHighlightingAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val (partToHighlight, color) = if (element is RustReferenceElement) {
            highlightReference(element)
        } else {
            highlightNotReference(element)
        } ?: return

        holder.createInfoAnnotation(partToHighlight, null).textAttributes = color.textAttributesKey
    }

    private fun highlightReference(element: RustReferenceElement): Pair<PsiElement, RustColor>? {
        val parent = element.parent
        val isPrimitiveType = element is RustPathElement && parent is RustBaseTypeElement &&
            RustTypificationEngine.typifyType(parent).isPrimitive

        val color = if (isPrimitiveType) {
            RustColor.PRIMITIVE_TYPE
        } else {
            val ref = element.reference.resolve() ?: return null
            // Highlight the element dependent on what it's referencing.
            colorFor(ref)
        }
        return color?.let { element.referenceNameElement to it }
    }

    private fun highlightNotReference(element: PsiElement): Pair<PsiElement, RustColor>? {
        if (element is RustLitExprElement) {
            if (element.parent is RustMetaItemElement) {
                val literal = element.firstChild
                val color = RustHighlighter.map(literal.elementType)
                    ?: return null // FIXME: `error` here perhaps?
                return literal to color
            }
            return null
        }

        // Although we remap tokens from identifier to keyword, this happens in the
        // parser's pass, so we can't use HighlightingLexer to color these
        if (element.elementType in RustTokenElementTypes.CONTEXTUAL_KEYWORDS) {
            return element to RustColor.KEYWORD
        }

        if (element is RustCompositeElement) {
            val color = colorFor(element)
            val part = partToHighlight(element)
            if (color != null && part != null) {
                return part to color
            }
        }
        return null
    }
}

// If possible, this should use only stubs because this will be called
// on elements in other files when highlighting references.
private fun colorFor(element: RustCompositeElement): RustColor? = when (element) {
    is RustAttrElement -> RustColor.ATTRIBUTE
    is RustMacroInvocationElement -> RustColor.MACRO
    is RustSelfParameterElement -> RustColor.SELF_PARAMETER
    is RustTryExprElement -> RustColor.Q_OPERATOR
    is RustTraitRefElement -> RustColor.TRAIT

    is RustEnumItemElement -> RustColor.ENUM
    is RustEnumVariantElement -> RustColor.ENUM_VARIANT
    is RustExternCrateItemElement -> RustColor.CRATE
    is RustFieldDeclElement -> RustColor.FIELD
    is RustFunctionElement -> when (element.role) {
        RustFunctionRole.FOREIGN, RustFunctionRole.FREE -> RustColor.FUNCTION
        RustFunctionRole.TRAIT_METHOD, RustFunctionRole.IMPL_METHOD ->
            if (element.isStatic) RustColor.ASSOC_FUNCTION else RustColor.METHOD
    }
    is RustMethodCallExprElement -> RustColor.METHOD
    is RustModDeclItemElement -> RustColor.MODULE
    is RustModItemElement -> RustColor.MODULE
    is RustPatBindingElement -> when {
        element.parentOfType<RustParameterElement>() != null -> RustColor.PARAMETER
        element.isMut -> RustColor.MUT_BINDING
        else -> null
    }
    is RustStructItemElement -> RustColor.STRUCT
    is RustTraitItemElement -> RustColor.TRAIT
    is RustTypeAliasElement -> RustColor.TYPE_ALIAS
    is RustTypeParamElement -> RustColor.TYPE_PARAMETER
    else -> null
}

private fun partToHighlight(element: RustCompositeElement): PsiElement? = when (element) {
    is RustAttrElement -> element
    is RustMacroInvocationElement -> element
    is RustSelfParameterElement -> element.self
    is RustTryExprElement -> element.q
    is RustTraitRefElement -> element.path.identifier

    is RustEnumItemElement -> element.identifier
    is RustEnumVariantElement -> element.identifier
    is RustExternCrateItemElement -> element.identifier
    is RustFieldDeclElement -> element.identifier
    is RustFunctionElement -> element.identifier
    is RustMethodCallExprElement -> element.identifier
    is RustModDeclItemElement -> element.identifier
    is RustModItemElement -> element.identifier
    is RustPatBindingElement -> element.identifier
    is RustStructItemElement -> element.identifier
    is RustTraitItemElement -> element.identifier
    is RustTypeAliasElement -> element.identifier
    is RustTypeParamElement -> element.identifier
    else -> null
}
