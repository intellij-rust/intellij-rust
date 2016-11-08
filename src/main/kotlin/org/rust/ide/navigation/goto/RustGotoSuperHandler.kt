package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.superMethod

class RustGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: file ?: return

        val modOrMethod = PsiTreeUtil.getParentOfType(
            focusedElement,
            RustImplMethodMemberElement::class.java,
            RustMod::class.java
        )

        when (modOrMethod) {
            is RustImplMethodMemberElement -> modOrMethod.superMethod?.navigate(true)
            is RustMod -> modOrMethod.`super`?.navigate(true)
        }
    }

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RustFile
}
