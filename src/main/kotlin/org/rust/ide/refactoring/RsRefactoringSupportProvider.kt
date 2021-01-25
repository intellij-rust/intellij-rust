/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.rust.ide.refactoring.changeSignature.RsChangeSignatureHandler
import org.rust.ide.refactoring.extractFunction.RsExtractFunctionHandler
import org.rust.ide.refactoring.introduceConstant.RsIntroduceConstantHandler
import org.rust.ide.refactoring.introduceParameter.RsIntroduceParameterHandler
import org.rust.ide.refactoring.introduceVariable.RsIntroduceVariableHandler
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner

class RsRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean =
        element is RsNameIdentifierOwner && !element.isExpandedFromMacro

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = RsIntroduceVariableHandler()

    // needed this one too to get it to show up in the dialog.
    override fun getIntroduceVariableHandler(element: PsiElement?): RefactoringActionHandler =
        RsIntroduceVariableHandler()

    override fun getIntroduceConstantHandler(): RefactoringActionHandler? = RsIntroduceConstantHandler()

    override fun getExtractMethodHandler(): RefactoringActionHandler = RsExtractFunctionHandler()

    override fun getIntroduceParameterHandler(): RefactoringActionHandler = RsIntroduceParameterHandler()

    override fun getChangeSignatureHandler(): ChangeSignatureHandler = RsChangeSignatureHandler()
}
