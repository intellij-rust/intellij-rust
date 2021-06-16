/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Key
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.cache.impl.IndexPatternUtil
import com.intellij.psi.impl.search.PsiTodoSearchHelperImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.PsiTodoSearchHelper
import org.rust.ide.colors.RsColor
import org.rust.ide.highlight.RsHighlighter
import org.rust.ide.todo.isTodoPattern
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.psi.RsDocLinkDestination
import org.rust.openapiext.getOrPut

class RsHighlightingAnnotator : AnnotatorBase() {

    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) return
        val color = when (element) {
            is LeafPsiElement -> highlightLeaf(element, holder)
            is RsAttr -> RsColor.ATTRIBUTE
            else -> null
        } ?: return

        if (!element.existsAfterExpansion) return
        if (element.ancestors.any { it is RsAttr && it.isDisabledCfgAttrAttribute }) return

        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        holder.newSilentAnnotation(severity).textAttributes(color.textAttributesKey).create()
    }

    private fun highlightLeaf(element: PsiElement, holder: AnnotationHolder): RsColor? {
        val parent = element.parent as? RsElement ?: return null

        if (parent is RsMetaVarIdentifier) {
            val metavarParent = parent.parent as? RsElement ?: return null
            return highlightIdentifier(parent, metavarParent, holder)
        }

        return when (element.elementType) {
            IDENTIFIER, QUOTE_IDENTIFIER, SELF -> highlightIdentifier(element, parent, holder)
            // Although we remap tokens from identifier to keyword, this happens in the
            // parser's pass, so we can't use HighlightingLexer to color these
            in RS_CONTEXTUAL_KEYWORDS -> RsColor.KEYWORD
            FLOAT_LITERAL -> RsColor.NUMBER

            Q -> if (parent is RsTryExpr) {
                RsColor.Q_OPERATOR
            } else {
                null
            }
            EXCL -> if (parent is RsMacro || parent is RsMacroCall && shouldHighlightMacroCall(parent, holder)) {
                RsColor.MACRO
            } else {
                null
            }
            in RS_LITERALS -> if (parent is RsLitExpr) {
                when (parent.parent) {
                    is RsMetaItem, is RsMetaItemArgs -> RsHighlighter.map(element.elementType)
                    else -> null
                }
            } else {
                null
            }
            else -> null
        }
    }

    private fun highlightIdentifier(element: PsiElement, parent: RsElement, holder: AnnotationHolder): RsColor? {
        return when {
            parent is RsReferenceElement && parent !is RsModDeclItem &&
                (parent !is RsPatBinding || parent.isReferenceToConstant) &&
                parent.referenceNameElement == element -> {
                highlightReference(parent, holder)
            }
            // Highlight `macro_rules`
            parent is RsMacro -> if (element == parent.identifier) {
                RsColor.MACRO
            } else {
                null
            }
            parent is RsNameIdentifierOwner && parent.nameIdentifier == element -> {
                colorFor(parent)
            }
            else -> null
        }
    }

    private fun highlightReference(element: RsReferenceElement, holder: AnnotationHolder): RsColor? {
        // These should be highlighted as keywords by the lexer
        if (element is RsPath && element.kind != PathKind.IDENTIFIER) return null
        if (element is RsPath && element.rootPath().parent is RsDocLinkDestination) return null
        if (element is RsExternCrateItem && element.self != null) return null

        val parent = element.parent
        val reference = element.reference
        val isPrimitiveType = element is RsPath && TyPrimitive.fromPath(element) != null &&
            (parent is RsBaseType || parent is RsPath && reference != null && reference.multiResolve().isEmpty())

        return when {
            isPrimitiveType -> RsColor.PRIMITIVE_TYPE
            parent is RsMacroCall -> if (shouldHighlightMacroCall(parent, holder)) RsColor.MACRO else null
            element is RsMethodCall -> RsColor.METHOD_CALL
            element is RsFieldLookup && element.identifier?.text == "await" && element.isAtLeastEdition2018 -> RsColor.KEYWORD
            element is RsPath && element.isCall() -> {
                val ref = reference?.resolve() ?: return null
                if (ref is RsFunction) {
                    when {
                        ref.isAssocFn -> RsColor.ASSOC_FUNCTION_CALL
                        ref.isMethod -> RsColor.METHOD_CALL
                        else -> RsColor.FUNCTION_CALL
                    }
                } else {
                    colorFor(ref)
                }
            }
            element is RsPath && parent is RsTraitRef -> RsColor.TRAIT
            else -> {
                val ref = reference?.resolve() ?: return null
                // Highlight the element dependent on what it's referencing.
                colorFor(ref)
            }
        }
    }

    private fun RsPath.isCall(): Boolean {
        var expr = parent?.parent
        while (expr is RsParenExpr) {
            expr = expr.parent
        }
        return expr is RsCallExpr
    }

    private fun shouldHighlightMacroCall(element: RsMacroCall, holder: AnnotationHolder): Boolean {
        if (element.macroName != "todo") return true
        return !isTodoHighlightingEnabled(element.containingFile, holder)
    }

    private fun isTodoHighlightingEnabled(file: PsiFile, holder: AnnotationHolder): Boolean {
        return holder.currentAnnotationSession.getOrPut(IS_TODO_HIGHLIGHTING_ENABLED) {
            val helper = PsiTodoSearchHelper.SERVICE.getInstance(file.project) as? PsiTodoSearchHelperImpl
                ?: return@getOrPut false
            if (!helper.shouldHighlightInEditor(file)) return@getOrPut false
            IndexPatternUtil.getIndexPatterns().any { it.isTodoPattern }
        }
    }

    companion object {
        private val IS_TODO_HIGHLIGHTING_ENABLED: Key<Boolean> = Key.create("IS_TODO_HIGHLIGHTING_ENABLED")
    }
}

// If possible, this should use only stubs because this will be called
// on elements in other files when highlighting references.
private fun colorFor(element: RsElement): RsColor? = when (element) {
    is RsMacro -> RsColor.MACRO
    is RsSelfParameter -> RsColor.SELF_PARAMETER
    is RsEnumItem -> RsColor.ENUM
    is RsEnumVariant -> RsColor.ENUM_VARIANT
    is RsExternCrateItem -> RsColor.CRATE
    is RsConstant -> RsColor.CONSTANT
    is RsNamedFieldDecl -> RsColor.FIELD
    is RsFunction -> when (element.owner) {
        is RsAbstractableOwner.Foreign, is RsAbstractableOwner.Free -> RsColor.FUNCTION
        is RsAbstractableOwner.Trait, is RsAbstractableOwner.Impl ->
            if (element.isAssocFn) RsColor.ASSOC_FUNCTION else RsColor.METHOD
    }
    is RsModDeclItem -> RsColor.MODULE
    is RsMod -> if (element.isCrateRoot) RsColor.CRATE else RsColor.MODULE
    is RsPatBinding -> {
        if (element.ancestorStrict<RsValueParameter>() != null) RsColor.PARAMETER else null
    }
    is RsStructItem -> when (element.kind) {
        RsStructKind.STRUCT -> RsColor.STRUCT
        RsStructKind.UNION -> RsColor.UNION
    }
    is RsTraitItem -> RsColor.TRAIT
    is RsTypeAlias -> RsColor.TYPE_ALIAS
    is RsTypeParameter -> RsColor.TYPE_PARAMETER
    is RsConstParameter -> RsColor.CONST_PARAMETER
    is RsMacroBinding -> RsColor.FUNCTION // TODO FUNCTION?
    else -> null
}
