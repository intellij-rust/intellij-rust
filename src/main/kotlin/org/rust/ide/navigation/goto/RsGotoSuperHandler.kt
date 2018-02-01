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
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.superItem

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
    val modOrAbstractable = PsiTreeUtil.getNonStrictParentOfType(
        source,
        RsAbstractable::class.java,
        RsMod::class.java
    ) ?: return null

    if (modOrAbstractable is RsAbstractable) {
        return if (modOrAbstractable.owner.isTraitImpl) {
            modOrAbstractable.superItem
        } else {
            gotoSuperTarget(modOrAbstractable.parent)
        }
    }

    val mod = modOrAbstractable as RsMod
    return when (mod) {
        is RsFile -> mod.declaration
        else -> mod.`super`
    }
}
