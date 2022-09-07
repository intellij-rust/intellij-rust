/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.util.BitUtil
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class RsTargetElementEvaluator : TargetElementEvaluatorEx, TargetElementEvaluatorEx2() {
    /**
     * Allows to intercept platform calls to [PsiReference.resolve]
     *
     * Note that if this method returns null, it means
     * "use default logic", i.e. call `ref.resolve()`
     */
    override fun getElementByReference(ref: PsiReference, flags: Int): PsiElement? {
        if (ref !is RsReference) return null

        // prefer pattern binding to its target if element name is accepted
        if (ref is RsPatBindingReferenceImpl && BitUtil.isSet(flags, TargetElementUtil.ELEMENT_NAME_ACCEPTED)) {
            return ref.element
        }

        if (ref is RsPathReference) {
            ref.tryResolveTypeAliasToImpl()?.let {
                return it
            }
        }

        // Filter invocations from CtrlMouseHandler (see RsQuickNavigationInfoTest)
        // and leave invocations from GotoDeclarationAction only.
        if (!RsGoToDeclarationRunningService.getInstance().isGoToDeclarationAction) return null

        return tryResolveToDeriveMetaItem(ref)
    }

    private fun tryResolveToDeriveMetaItem(ref: PsiReference): PsiElement? {
        val target = ref.resolve() as? RsAbstractable ?: return null
        val trait = (target.owner as? RsAbstractableOwner.Trait)?.trait ?: return null
        val item = when (val element = ref.element) {
            is RsPath -> element.path?.reference?.deepResolve() as? RsStructOrEnumItemElement
            else -> {
                val receiver = when (element) {
                    is RsMethodCall -> element.parentDotExpr.expr.type
                    is RsBinaryOp -> (element.parent as? RsBinaryExpr)?.left?.type
                    else -> return null
                }
                (receiver as? TyAdt)?.item
            }
        }

        return item?.derivedTraitsToMetaItems?.get(trait)
    }

    /**
     * Used to get parent named element when [element] is a name identifier
     *
     * Note that if this method returns null, it means "use default logic"
     */
    override fun getNamedElement(element: PsiElement): PsiElement? {
        // This hack enables some actions (e.g. "find usages") when the [element] is inside a macro
        // call and this element expands to name identifier of some named element.
        val elementType = element.elementType
        if (elementType == RsElementTypes.IDENTIFIER || elementType == RsElementTypes.QUOTE_IDENTIFIER) {
            val delegate = element.findExpansionElements()?.firstOrNull() ?: return null
            val delegateParent = delegate.parent
            if (delegateParent is RsNameIdentifierOwner && delegateParent.nameIdentifier == delegate) {
                return delegateParent
            }
        }

        return null
    }

    // TODO: this override exists only as a hack to solve issue #8309. Remove this if a better solution is found.
    //      Ideally the platform would add a proper extension point to customize text replacement logic.
    override fun isIdentifierPart(element: PsiFile, text: CharSequence, offset: Int): Boolean {
        val charAt = text[offset]
        if (charAt.isJavaIdentifierPart()) return true
        if (charAt != ':') return false
        val prev = text.elementAtOrNull(offset - 1)
        val next = text.elementAtOrNull(offset + 1)
        // We consider the path part separator `::` to be a part of identifier.
        // This is used in completion of associated items, because the built-in symbol insertion algorithm
        // has annoying behaviour otherwise. See https://github.com/intellij-rust/intellij-rust/issues/8309
        // We do not allow 3 or more consecutive `:` to be part of identifiers.
        return (next != ':' && prev == ':' && text.elementAtOrNull(offset - 2) != ':')
            || (prev != ':' && next == ':' && text.elementAtOrNull(offset + 2) != ':')
    }
}
