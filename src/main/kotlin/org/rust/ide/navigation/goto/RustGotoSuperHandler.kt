package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.RustFunctionKind
import org.rust.lang.core.psi.impl.mixin.kind
import org.rust.lang.core.psi.impl.mixin.superMethod

class RustGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: file ?: return

        val target = findTarget(focusedElement)
        when (target) {
            is RustFunctionElement -> target.superMethod?.navigate(true)
            is RustMod -> target.`super`?.navigate(true)
        }
    }

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RustFile

    private fun findTarget(source: PsiElement) : RustCompositeElement? /* RustMod | RustFunction*/ {
        val modOrMethod = PsiTreeUtil.getParentOfType(
            source,
            RustFunctionElement::class.java,
            RustMod::class.java
        ) ?: return null

        if (modOrMethod is RustFunctionElement && modOrMethod.kind != RustFunctionKind.IMPL_METHOD) {
            return findTarget(modOrMethod)
        }

        return modOrMethod
    }
}
