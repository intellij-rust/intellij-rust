/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.util.BitUtil
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsPatBindingReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class RsTargetElementEvaluator : TargetElementEvaluatorEx2() {
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
        if (element.elementType == RsElementTypes.IDENTIFIER) {
            val delegate = element.findExpansionElements()?.firstOrNull() ?: return null
            val delegateParent = delegate.parent
            if (delegateParent is RsNameIdentifierOwner && delegateParent.nameIdentifier == delegate) {
                return delegateParent
            }
        }

        return null
    }
}
