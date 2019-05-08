/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*
import java.nio.file.Path

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
    val it = PsiTreeUtil.getNonStrictParentOfType(
        source,
        RsAbstractable::class.java,
        RsMod::class.java
    )
    if (it is RsAbstractable) return when {
        it.owner.isTraitImpl -> it.superItem
        else -> gotoSuperTarget(it.parent)
    }

    if (it is RsMod) {
        return if (it is RsFile) {
            if (it.isCrateRoot) {
                it.containingCargoPackage
                    ?.rootDirectory
                    ?.resolve("Cargo.toml")
                    ?.toCargoTomlPsiFile(source.project)
            } else {
                it.declaration
            }
        } else {
            it.`super`
        }
    }

    return null
}

fun Path.toCargoTomlPsiFile(project: Project): PsiFile? {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath("$this") ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
    if (psiFile.fileType.name != "TOML") return null
    return psiFile
}
