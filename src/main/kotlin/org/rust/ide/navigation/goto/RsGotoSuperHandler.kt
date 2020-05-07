/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.toPsiFile

class RsGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RsFile

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: file
        val targets = gotoSuperTargets(focusedElement)
        when (targets.size) {
            0 -> return
            1 -> targets.single().navigate(true)
            else -> NavigationUtil.getPsiElementPopup(targets.toTypedArray(), "Choose module declaration")
                .showInBestPositionFor(editor)
        }
    }
}

@VisibleForTesting
fun gotoSuperTargets(source: PsiElement): List<NavigatablePsiElement> {
    val item = PsiTreeUtil.getNonStrictParentOfType(
        source,
        RsAbstractable::class.java,
        RsMod::class.java
    )
    if (item is RsAbstractable) return when {
        item.owner.isTraitImpl -> listOfNotNull(item.superItem)
        else -> gotoSuperTargets(item.parent)
    }

    if (item is RsMod) {
        return if (item is RsFile) {
            if (item.isCrateRoot) {
                val manifestPath = item.containingCargoPackage?.rootDirectory?.resolve("Cargo.toml") ?: return emptyList()
                listOfNotNull(item.virtualFile?.fileSystem?.findFileByPath(manifestPath.toString())?.toPsiFile(item.project))
            } else {
                item.declarations
            }
        } else {
            listOfNotNull(item.`super`)
        }
    }

    return emptyList()
}
