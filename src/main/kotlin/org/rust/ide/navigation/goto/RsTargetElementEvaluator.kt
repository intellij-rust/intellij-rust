/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.macros.findMacroCallExpandedFrom

class RsTargetElementEvaluator : TargetElementEvaluatorEx2() {
    /**
     * Allows to intercept platform calls to [PsiReference.resolve]
     *
     * Note that if this method returns null, it means
     * "use default logic", i.e. call `ref.resolve()`
     */
    override fun getElementByReference(ref: PsiReference, flags: Int): PsiElement? = null

    /**
     * Allows to refine GotoDeclaration target
     *
     * Note that if this method returns null, it means
     * "use default logic", i.e. just use `navElement`
     *
     * @param element the resolved element (basically via `element.reference.resolve()`)
     * @param navElement the element we going to navigate to ([PsiElement.getNavigationElement])
     */
    override fun getGotoDeclarationTarget(element: PsiElement, navElement: PsiElement?): PsiElement? =
        element.findMacroCallExpandedFrom()?.referenceNameElement
}
