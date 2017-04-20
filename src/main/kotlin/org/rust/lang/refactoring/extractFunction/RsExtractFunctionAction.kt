package org.rust.lang.refactoring.extractFunction

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BasePlatformRefactoringAction
import org.rust.lang.refactoring.RsRefactoringSupportProvider

class RsExtractFunctionAction : BasePlatformRefactoringAction() {
    override fun getRefactoringHandler(provider: RefactoringSupportProvider): RefactoringActionHandler? =
        RsExtractFunctionHandler()

    override fun isAvailableInEditorOnly(): Boolean = true
}
