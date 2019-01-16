/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import org.rust.ide.refactoring.extractFunction.RsExtractFunctionHandler
import org.rust.ide.refactoring.introduceParameter.RsIntroduceParameterHandler
import org.rust.ide.refactoring.introduceVariable.RsIntroduceVariableHandler
import org.rust.lang.core.psi.RsPatBinding

class RsRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = element is RsPatBinding

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = RsIntroduceVariableHandler()

    // needed this one too to get it to show up in the dialog.
    override fun getIntroduceVariableHandler(element: PsiElement?): RefactoringActionHandler =
        RsIntroduceVariableHandler()

    override fun getExtractMethodHandler(): RefactoringActionHandler = RsExtractFunctionHandler()

    override fun getIntroduceParameterHandler(): RefactoringActionHandler = RsIntroduceParameterHandler()
}
