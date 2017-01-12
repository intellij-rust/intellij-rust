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
import org.rust.lang.core.types.isPrimitive
import org.rust.lang.core.types.util.resolvedType

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
        val isPrimitiveType = element is RsPath && parent is RsBaseType &&
            parent.resolvedType.isPrimitive

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
        if (element is RsLitExpr) {
            if (element.parent is RsMetaItem) {
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
    is RsAttr -> RustColor.ATTRIBUTE
    is RsMacroInvocation -> RustColor.MACRO
    is RsSelfParameter -> RustColor.SELF_PARAMETER
    is RsTryExpr -> RustColor.Q_OPERATOR
    is RsTraitRef -> RustColor.TRAIT

    is RsEnumItem -> RustColor.ENUM
    is RsEnumVariant -> RustColor.ENUM_VARIANT
    is RsExternCrateItem -> RustColor.CRATE
    is RsFieldDecl -> RustColor.FIELD
    is RsFunction -> when (element.role) {
        RustFunctionRole.FOREIGN, RustFunctionRole.FREE -> RustColor.FUNCTION
        RustFunctionRole.TRAIT_METHOD, RustFunctionRole.IMPL_METHOD ->
            if (element.isStatic) RustColor.ASSOC_FUNCTION else RustColor.METHOD
    }
    is RsMethodCallExpr -> RustColor.METHOD
    is RsModDeclItem -> RustColor.MODULE
    is RsModItem -> RustColor.MODULE
    is RsPatBinding -> when {
        element.parentOfType<RsValueParameter>() != null -> RustColor.PARAMETER
        element.isMut -> RustColor.MUT_BINDING
        else -> null
    }
    is RsStructItem -> RustColor.STRUCT
    is RsTraitItem -> RustColor.TRAIT
    is RsTypeAlias -> RustColor.TYPE_ALIAS
    is RsTypeParameter -> RustColor.TYPE_PARAMETER
    else -> null
}

private fun partToHighlight(element: RustCompositeElement): PsiElement? = when (element) {
    is RsAttr -> element
    is RsMacroInvocation -> element
    is RsSelfParameter -> element.self
    is RsTryExpr -> element.q
    is RsTraitRef -> element.path.identifier

    is RsEnumItem -> element.identifier
    is RsEnumVariant -> element.identifier
    is RsExternCrateItem -> element.identifier
    is RsFieldDecl -> element.identifier
    is RsFunction -> element.identifier
    is RsMethodCallExpr -> element.identifier
    is RsModDeclItem -> element.identifier
    is RsModItem -> element.identifier
    is RsPatBinding -> element.identifier
    is RsStructItem -> element.identifier
    is RsTraitItem -> element.identifier
    is RsTypeAlias -> element.identifier
    is RsTypeParameter -> element.identifier
    else -> null
}
