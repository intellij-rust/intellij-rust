/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.refactoring.introduceVariable.RsIntroduceVariableHandler

class RsRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = element is RsPatBinding

    override fun getIntroduceVariableHandler() = RsIntroduceVariableHandler()

    // needed this one too to get it to show up in the dialog.
    override fun getIntroduceVariableHandler(element: PsiElement?) = RsIntroduceVariableHandler()

}
