/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.superMethod

class RsGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RsFile

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: file
            ?: return
        gotoSuperTarget(focusedElement)?.navigate(true)
    }
}

// public for testing
fun gotoSuperTarget(source: PsiElement): NavigatablePsiElement? {
    val modOrMethod = PsiTreeUtil.getNonStrictParentOfType(
        source,
        RsFunction::class.java,
        RsMod::class.java
    ) ?: return null

    if (modOrMethod is RsFunction) {
        return if (modOrMethod.owner.isTraitImpl) {
            modOrMethod.superMethod
        } else {
            gotoSuperTarget(modOrMethod.parent)
        }
    }

    val mod = modOrMethod as RsMod
    return when (mod) {
        is RsFile -> mod.declaration
        else -> mod.`super`
    }
}
