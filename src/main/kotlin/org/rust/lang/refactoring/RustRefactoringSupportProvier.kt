package org.rust.lang.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPatBindingElement

class RustRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return element is RustPatBindingElement
    }
}
