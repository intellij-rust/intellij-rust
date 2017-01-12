package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.RustFunctionRole
import org.rust.lang.core.psi.impl.mixin.role
import org.rust.lang.core.psi.impl.mixin.superMethod

class RsGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: file ?: return

        val target = findTarget(focusedElement)
        when (target) {
            is RsFunction -> target.superMethod?.navigate(true)
            is RsMod -> target.`super`?.navigate(true)
        }
    }

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RsFile

    private fun findTarget(source: PsiElement): RsCompositeElement? /* RustMod | RustFunction*/ {
        val modOrMethod = PsiTreeUtil.getParentOfType(
            source,
            RsFunction::class.java,
            RsMod::class.java
        ) ?: return null

        if (modOrMethod is RsFunction && modOrMethod.role != RustFunctionRole.IMPL_METHOD) {
            return findTarget(modOrMethod)
        }

        return modOrMethod
    }
}
